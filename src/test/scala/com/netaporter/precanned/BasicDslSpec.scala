package com.netaporter.precanned

import com.netaporter.precanned.HttpServerMock.PrecannedResponseAdded
import org.scalatest.{ BeforeAndAfterAll, Matchers, BeforeAndAfter, FlatSpecLike, OptionValues }
import dsl.basic._
import spray.client.pipelining._
import spray.http.HttpEntity
import scala.concurrent.Await
import spray.http.StatusCodes._
import scala.concurrent.duration._

class BasicDslSpec
    extends FlatSpecLike
    with Matchers
    with BeforeAndAfter
    with BeforeAndAfterAll
    with OptionValues
    with BaseSpec {

  val port = 8765
  val animalApi = httpServerMock(system).bind(8765).block

  after { animalApi.clearExpectations() }
  override def afterAll() { system.shutdown() }

  "query expectation" should "match in any order" in {
    animalApi.expect(query("key1" -> "val1", "key2" -> "val2"))
      .andRespondWith(resource("/responses/animals.json")).blockFor(3.seconds)

    val resF = pipeline(Get(s"http://127.0.0.1:$port?key2=val2&key1=val1"))
    val res = Await.result(resF, dur)

    res.entity.asString should equal("""[{"name": "rhino"}, {"name": "giraffe"}, {"name": "tiger"}]""")
  }

  "path expectation" should "match path" in {
    animalApi.expect(path("/animals"))
      .andRespondWith(resource("/responses/animals.json"))

    val resF = pipeline(Get(s"http://127.0.0.1:$port/animals"))
    val res = Await.result(resF, dur)

    res.entity.asString should equal("""[{"name": "rhino"}, {"name": "giraffe"}, {"name": "tiger"}]""")
  }

  "several expectation" should "work together" in {
    animalApi.expect(get, path("/animals"), query("name" -> "giraffe"))
      .andRespondWith(resource("/responses/giraffe.json")).blockFor(3.seconds)

    val resF = pipeline(Get(s"http://127.0.0.1:$port/animals?name=giraffe"))
    val res = Await.result(resF, dur)

    res.entity.asString should equal("""{"name": "giraffe"}""")
  }

  "earlier expectations" should "take precedence" in {
    animalApi.expect(get, path("/animals"))
      .andRespondWith(resource("/responses/animals.json")).blockFor(3.seconds)

    animalApi.expect(get, path("/animals"), query("name" -> "giraffe"))
      .andRespondWith(resource("/responses/giraffe.json"))

    val resF = pipeline(Get(s"http://127.0.0.1:$port/animals?name=giraffe"))
    val res = Await.result(resF, dur)

    res.entity.asString should equal("""[{"name": "rhino"}, {"name": "giraffe"}, {"name": "tiger"}]""")
  }

  "unmatched requests" should "return 404" in {
    animalApi.expect(get, path("/animals")).andRespondWith(resource("/responses/animals.json")).blockFor(3.seconds)

    val resF = pipeline(Get(s"http://127.0.0.1:$port/hotdogs"))
    val res = Await.result(resF, dur)

    res.status should equal(NotFound)
  }

  "custom status code with entity" should "return as expected" in {

    animalApi.expect(get, path("/animals")).andRespondWith(status(404), entity(HttpEntity("""{"error": "animals not found"}"""))).blockFor(3.seconds)

    val resF = pipeline(Get(s"http://127.0.0.1:$port/animals"))
    val res = Await.result(resF, dur)

    res.status should equal(NotFound)
    res.entity.toOption.value.asString should equal("""{"error": "animals not found"}""")
  }

  "post request non empty content " should "match exactly" in {
    val postContent: String = """ {"name":"gorilla gustav"} """
    animalApi.expect(post, path("/animals"), exactContent(postContent)).andRespondWith(entity(HttpEntity("""{"record":"created" """))).blockFor(3.seconds)

    val resF = pipeline(Post(s"http://127.0.0.1:$port/animals", postContent))
    val res = Await.result(resF, dur)

    res.entity.toOption.value.asString should equal("""{"record":"created" """)
  }

  "post request empty content " should "match" in {
    animalApi.expect(post, path("/animals"), exactContent()).andRespondWith(entity(HttpEntity("""{"error":"name not provided" """))).blockFor(3.seconds)

    val resF = pipeline(Post(s"http://127.0.0.1:$port/animals"))
    val res = Await.result(resF, dur)

    res.entity.toOption.value.asString should equal("""{"error":"name not provided" """)
  }

  "post request non empty content " should "match partially" in {
    val postContent: String = """ {"name":"gorilla gustav"} """
    animalApi.expect(post, path("/animals"), containsContent("gorilla gustav")).andRespondWith(entity(HttpEntity("""{"record":"created" """))).blockFor(3.seconds)

    val resF = pipeline(Post(s"http://127.0.0.1:$port/animals", postContent))
    val res = Await.result(resF, dur)

    res.entity.toOption.value.asString should equal("""{"record":"created" """)
  }

  "a delay" should "cause the response to be delayed" in {
    animalApi.expect(get, path("/animals")).andRespondWith(status(200), delay(5.seconds)).blockFor(3.seconds)
    val resF = pipeline(Get(s"http://127.0.0.1:$port/animals"))

    Thread.sleep(4000l)
    resF.isCompleted should equal(false)

    val res = Await.result(resF, dur)
    res.status.intValue should equal(200)
  }

  "blockFor" should "block until the expectation is added and return confirmation" in {
    val blocked = animalApi.expect(get, path("/animals")).andRespondWith(status(200), delay(5.seconds)).blockFor(3.seconds)
    blocked should equal(PrecannedResponseAdded)
  }
}
