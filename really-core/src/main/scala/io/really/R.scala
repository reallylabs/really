package io.really

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.parsing.combinator.JavaTokenParsers

/*
 * Represents the id part of each [[io.really.PathToken]] in an [[[[io.really.R]]]
 * May be a wildcard or actual long id.
 */
sealed trait TokenId {
  /*
   * Returns true only if this instance id wildcard [[io.really.R.*]]
   */
  def isWildcard: Boolean
  /*
   * Returns [[scala.Some]] of [[scala.Long]] id if this instace is not a wildcard
   * otherwise, [[scala.None]] is returned
   */
  def asOpt: Option[Long]
  /*
   * Returns [[scala.Long]] id if this instance is not a wildcard,
   * may raise exception if this instance is wildcard.
   */
  def get: Long
}

/*
 * Represents the root path: "/".
 * Used as the first building block to create complex paths programmatically
 * {{{
 * scala> R / "users" / 1234 / "posts" / 5678
 * res3: io.really.R = /users/1234/posts/5678/
 * }}}
 */
object R extends R(Nil) {
  /*
   * Represents the actual id of a specific object
   * {{{
   * scala> R / "users" / 1234
   * res3: io.really.R = /users/1234/
   * }}}
   */
  final case class IdValue(id: Long) extends TokenId {
    def isWildcard = false
    lazy val asOpt = Some(id)
    lazy val get = id
    override def toString() = id.toString
  }

  /*
   * Represents the wildcard id, used to denote the whole collection
   * {{{
   * scala> R / "users" / R.*
   * res3: io.really.R = /users/ * /
   * }}}
   */
  final case object * extends TokenId {
    lazy val asOpt = None
    def get = throw new NoSuchElementException
    override def isWildcard = true
  }

  def fromTokens(tokens: Tokens) =
    tokens.foldLeft(R: R)(_ / _)

  private val parser = new RParser

  /*
   * Creates R instance by parsing it's string representation.
   * Raises [[IllegalArgumentException]] if string was not a valid R
   * {{{
   * scala> R("/users/1234/posts/5678")
   * res3: io.really.R = /users/1234/posts/5678/
   * }}}
   */
  def apply(s: String): R = parser.parse(s) match {
    case parser.Success(r, _) =>
      r
    case parser.NoSuccess(message, in) =>
      throw new IllegalArgumentException(s"Could not parse R. Reason: $message")
  }

  /*
   * JSON Writes for R
   */
  implicit val RWrites = new Writes[R] {
    def writes(r: R): JsValue = JsString(r.toString())
  }

  /*
   * JSON Reads for R
   */
  implicit val RReads = new Reads[R] {
    def reads(r: JsValue): JsResult[R] =
      r match {
        case JsString(r) =>
          try {
            JsSuccess(R(r))
          } catch {
            case e: IllegalArgumentException =>
              JsError(Seq(JsPath() -> Seq(ValidationError("error.unexpected.r.format"))))
          }
        case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.jsstring"))))
      }
  }

}

/**
 * A URI-like logical path that represents an object or collection.
 * ==Usage==
 * - Top level collection
 * {{{
 * scala> R / "users"
 * res0: io.really.R = /users/ * /
 * }}}
 * - Top level object
 * {{{
 * scala> R / "users" / 1234
 * res1: io.really.R = /users/1234/
 * }}}
 * - Nested collection
 * {{{
 * scala> R / "users" / 1234 / "posts"
 * res2: io.really.R = /users/1234/posts/ * /
 * }}}
 * - Wildcard collection.
 * {{{
 * scala> R / "users" / R.* / "posts"
 * res4: io.really.R = /users/ * /posts/ * /
 * }}}
 * - Extracting the last token
 * {{{
 * scala> val _ / lastToken = R("/users/1234/")
 * lastToken: io.really.R = /users/ * /
 * }}}
 * - Extracting the last token's collection and id
 * {{{
 * scala> val _ / (collection / id) = R("/users/1234")
 * collection: String = users
 * id: io.really.TokenId = 1234
 * }}}
 * - Extracting the parent path
 * {{{
 * scala> val parent / _ = R("/users/1234/comment/456")
 * parent: io.really.R = /users/1234/
 * }}}
 * - Combo example
 * {{{
 * scala> val R / ("users" / userId) / (collection / id) = R("/users/1234/comments/456")
 * userId: io.really.TokenId = 1234
 * collection: String = comments
 * id: io.really.TokenId = 456
 * rev: io.really.Revision = 55
 * }}}
 */
case class R private(tokens: Tokens) {

  private def addToken(token: PathToken, tokens: Tokens): Tokens = token.id match {
    case _: R.IdValue => //no loopholes allowed
      if (tokens.exists(_.isWildcard))
        throw new IllegalArgumentException("Assigning an ID to an R with /*/ in it")
      else token :: tokens
    case _ => token :: tokens
  }

  /*
   * Appends path token to the end of the path
   * {{{
   * scala> R / PathToken("users", 123)
   * res3: io.really.R = /users/123/
   * }}}
   * @param token path token, either wildcard or with a specific id
   * @return a new R with the new appended PathToken
   */
  def /(token: PathToken): R = copy(tokens = addToken(token, tokens))
  /*
   * Appends wildcard path token
   * {{{
   * scala> R / "users"
   * res0: io.really.R = /users/ * /
   * }}}
   * @param collection collection name as a string
   * @return a new R with the a new appended PathToken of the collection name and a wildcard id
   */
  def /(collection: CollectionName): R = / (PathToken(collection, R.*))

  /*
   * Appends wildcard path token
   * {{{
   * scala> R / 'users
   * res0: io.really.R = /users/ * /
   * }}}
   * @param collection collection name as a symbol
   * @return a new R with the a new appended PathToken of the collection name and a wildcard id
   */
  def /(collection: Symbol): R = / (collection.name)

  /*
   * Appends an id (ValueId or Wildcard) to last path token (head)
   * {{{
   * scala> R / 'users / 123
   * res0: io.really.R = /users/123/
   * }}}
   * @param id either wildcard (R.*), or ValueId (also accepts [[scala.Int]] and [[scala.Long]] using implicit conversions)
   * @return a new R with a modified head to contain the new id
   */
  def /(id: TokenId): R = tokens match {
    case (x@PathToken(_, R.*)) :: xs =>
      copy(tokens = addToken(x.setId(id), xs))
    case Nil =>
      throw new IllegalArgumentException("Assigning ID to an empty R")
    case PathToken(_, R.IdValue(id)) :: _ =>
      throw new IllegalArgumentException(s"Assigning ID to a path already with id: %id")
  }

  def head = tokens.head
  def tail = tokens.tail

  /*
   * Stripped down version with each ValueId replaced by wildcard R.*
   */
  lazy val skeleton = R(tokens.map(a => PathToken(a.collection, R.*)))

  /*
   * Returns true if the two Rs has the same collection names with the same
   * order ignoring the ids
   */
  def looksLike(r: R): Boolean = this.skeleton == r.skeleton

  def actorFriendlyStr = this.toString.replace('/', '_')

  def isObject: Boolean = this.head.id.asOpt map(_ => true) getOrElse(false)

  def isCollection: Boolean = !isObject

  def tailR: R = R(tail)

  def inversedTail: R = R(tokens.dropRight(1))

  override def toString() =
    tokens.foldRight("/")((acc, v) => v + acc.toString)
}

/*
 * Represents one token in R pathes, consists of collection's name and id.
 */
case class PathToken(collection: String, id: TokenId) {
  /*
   * returns a copy of [[io.really.PathToken]] with the new id.
   */
  def setId(id: TokenId): PathToken = PathToken(collection, id)

  /*
   * returns true if id is wildcard.
   */
  def isWildcard = id.isWildcard

  override def toString() = s"$collection/$id/"
}

/*
 * Extractor object that splits R into collections and ids
 */
object / {
  /*
   * Splits R into parent R and last path token
   */
  def unapply(r: R): Option[(R, PathToken)] = r.tokens match {
    case head :: tail => Some(R.fromTokens(tail) -> head)
    case _ => None
  }

  /*
   * Splits path token into collection name and id
   */
  def unapply(token: PathToken): Option[(String, TokenId)] = token match {
    case PathToken(collection, id) => Some(collection -> id)
    case _ => None
  }

}

/*
 * Parses R out of strings, should not be used directly
 * instead use constructor of R
 * {{{
 * scala> R("/users/1234/posts/5678")
 * res3: io.really.R = /users/1234/posts/5678/
 * }}}
 */
class RParser extends JavaTokenParsers {
  override val skipWhitespace = false

  private val collection: Parser[CollectionName] =
    "/" ~> """\p{javaJavaIdentifierStart}[\p{javaJavaIdentifierPart}-]*""".r

  private val rev: Parser[Revision] =
    ("/@" ~> """\p{Alnum}+""".r) ^^ { case rev => rev.toLong }

  private val valueId: Parser[R.IdValue] =
    ("/" ~> """\p{Alnum}+""".r) ^^ { case id => R.IdValue(id.toLong) }

  private val wildcardId: Parser[TokenId] =
    "/*" ^^^ R.*

  private val tokenId: Parser[TokenId] =
    wildcardId | valueId

  private val pathToken: Parser[PathToken] =
    collection ~ tokenId.? ^^ {
      case c ~ Some(id) => PathToken(c, id)
      case c ~ None => PathToken(c, R.*)
    }

  private val tokens: Parser[List[PathToken]] =
    rep(pathToken)

  private val optionalTrailingSlash = "/".?

  private val rTokens: Parser[R] =
    tokens ^^ {
      case tokens => R.fromTokens(tokens)
    }

  private val r: Parser[R] =
    rTokens ~ optionalTrailingSlash ^^ {
      case r ~ _ =>
        r
    }

  def parse(input: String): ParseResult[R] =
    parseAll(r, input)
}
