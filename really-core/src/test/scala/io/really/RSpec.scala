package io.really

import org.scalatest._

class RSpec extends FlatSpec {
  "R" should "throw IllegalArgumentException when trying to create an R with ID without collection" in {
    intercept[IllegalArgumentException] {
      R / 41252
    }
  }
  it should "returns an empty list if you call tail on a solo R" in {
    val a = R / "users" / R.*
    assert(a.tail == List())
  }
  it should "throw IllegalArgumentException if loophole detected" in {
    intercept[IllegalArgumentException] {
      R / "users" / R.* / "books" / 66
    }
  }
  it should "throw IllegalArgumentException if contained two successive ids" in {
    intercept[IllegalArgumentException] {
      R / "users" / 55 / 11
    }
  }
  it should "put wildcards automatically in DSL if no ID was specified" in {
    val a = R / "users"
    assert(a.head.isWildcard == true)
  }
  it should "set the ID correctly if passed as long or int" in {
    val a = R / "users" / 112l
    assert(a.head.id.asOpt == Some(112l))
    val b = R / "users" / 12
    assert(b.head.id.asOpt == Some(12l))
  }
  it should "construct skeleton R with DSL easily as in R/users/friends  => /users/*/friends/*/" in {
    assert((R / "users" / "friends").toString == "/users/*/friends/*/")
  }
  it should "construct R with symbols too R / 'users / 55 / 'friends" in {
    assert((R / 'users / 'friends).toString == "/users/*/friends/*/")
  }
  it should "support equals" in {
    val a = R / 'users / 512 / 'books / 442 / 'book / 222
    val b = R / 'users / 512 / 'books / 442 / 'book / 222
    assert (a == b)
  }
  it should "support looksLike comparison that ignore ids and just checks for skeletons" in {
    val a = R / 'users / 115 / 'books / 445 / 'book / 222
    val b = R / 'users / 512 / 'books / 442 / 'book / 222
    val c = R / 'users / 512 / 'books / 222
    assert(a looksLike b)
    assert(! (a looksLike c))
  }
  "R.toString" should "resolve to /users/*/friends/*/" in {
    assert((R / 'users/'friends).toString == "/users/*/friends/*/")
  }
  it should "resolve to / for root path" in {
    assert(R.toString == "/")
  }
  it should "resolve to /users/221/friends/552" in {
    assert((R/'users/221/'friends/552).toString == "/users/221/friends/552/")
  }
  "R parser" should "parse top level wildcard collection" in {
    assert(R("/users/*") == R / 'users / R.*)
  }
  it should "parse top level collection name (asterisk is optional)" in {
    assert(R("/users") == R / 'users)
  }
  it should "parse top level object" in {
    assert(R("/users/123") == R / 'users / 123)
  }
  it should "parse nested wildcard collection under top level object" in {
    assert(R("/users/123/posts/*") == R / 'users / 123 / 'posts)
  }
  it should "parse nested collection name (asterisk is optional)" in {
    assert(R("/users/123/posts") == R / 'users / 123 / 'posts)
  }
  it should "parse with extra trailing slash" in {
    assert(R("/users/221/friends/552/") == R / 'users / 221 / 'friends / 552)
    assert(R("/users/221/friends") == R / 'users / 221 / 'friends)
  }
  it should "not accept two extra trailing slashes" in {
    intercept[IllegalArgumentException] {
      R("/users/221/friends/123//")
    }
    intercept[IllegalArgumentException] {
      R("/users/221/friends//")
    }
  }
  it should "fail if got two successive collection" in {
    intercept[IllegalArgumentException] {
      R("/users/posts")
    }
  }
  it should "fail if got two successive ids" in {
    intercept[IllegalArgumentException] {
      R("/users/123/456")
    }
  }
  it should "fail if got id instead of collection name" in {
    intercept[IllegalArgumentException] {
      R("/123")
    }
  }
  it should "fail if got loophole path" in {
    intercept[IllegalArgumentException] {
      R("/users/*/posts/123")
    }
  }
  "/ matcher" should "split R into tokens" in {
    val R / userToken / bookToken = R / 'users / 115 / 'books / R.*
    assert(userToken == PathToken("users", R.IdValue(115)))
    assert(bookToken == PathToken("books", R.*))
  }
  it should "also split tokens into collection and Id" in {
    val collection / R.IdValue(id) = PathToken("users", R.IdValue(115))
    assert(collection == "users")
    assert(id == 115)
  }
  it should "split R to tokens, then each token into collection and id" in {
    val R / ("users" / userId) / (collectionName / R.*) = R / 'users / 115 / 'books / R.*
    assert(collectionName == "books")
    assert(userId == R.IdValue(115))
  }
  it should "play nice with / matcher" in {
    val R / ("users" / R.IdValue(userId)) / ("books" / R.IdValue(bookId)) = R / 'users / 115 / 'books / 445
    assert(userId == 115)
    assert(bookId == 445)
  }
  it should "have a helper indicate whether the R refers to an object or not" in {
    val r1 = R / 'users / 123
    val r2 = R / 'users
    assert(r1.isObject == true)
    assert(r2.isObject == false)
  }
  it should "have a helper indicate whether the R refers to a collection or not" in {
    val r1 = R / 'users / 123
    val r2 = R / 'users
    assert(r1.isCollection == false)
    assert(r2.isCollection == true)
  }
}
