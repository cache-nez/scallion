/* Copyright 2019 EPFL, Lausanne
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package scallion.parsing

import scala.collection.immutable.ListSet

import scallion.util.internal.{Producer, ProducerOps}

/** Contains definitions relating to parsers.
  *
  * @see See trait [[scallion.parsing.Operators]] for useful combinators
  *      to describe infix, prefix and postfix operators.
  *
  * @group parsing
  */
trait Parsers[Token, Kind]
    extends visualization.Graphs[Kind]
       with visualization.Grammars[Kind] {

  import Parser._

  /** Returns the kind associated with `token`.
    *
    * @group abstract
    */
  def getKind(token: Token): Kind

  /** Sequence of token kinds.
    *
    * @group other
    */
  type Trail = Seq[Kind]

  /** Contains utilities to build trails.
    *
    * @group other
    */
  private object Trail {

    /** The empty trail. */
    val empty: Trail = Vector()

    /** Returns a trail containing a single `kind`. */
    def single(kind: Kind): Trail = Vector(kind)
  }

  /** Contains utilies to produce trails. */
  private object trailOps extends ProducerOps[Trail] {

    /** Concatenation of trails. */
    override def join(left: Trail, right: Trail): Trail =
      left ++ right

    /** Comparison of trails by size. */
    override def lessEquals(left: Trail, right: Trail): Boolean =
      left.size <= right.size
  }

  private object tokenSeqOps extends ProducerOps[Seq[Token]] {

    /** Concatenation of token sequences. */
    override def join(left: Seq[Token], right: Seq[Token]): Seq[Token] =
      left ++ right

    /** Comparison of trails by size. */
    override def lessEquals(left: Seq[Token], right: Seq[Token]): Boolean =
      left.size <= right.size
  }

  /** Consumes a stream of tokens and tries to produces a value of type `A`.
    *
    * @group parser
    *
    * @groupprio subparser 2
    * @groupname subparser Member Parsers
    *
    * @groupprio parsing 5
    * @groupname parsing Parsing
    *
    * @groupprio complete 6
    * @groupname complete Completions
    *
    * @groupprio property 7
    * @groupname property Properties
    */
  sealed trait Parser[+A] {

    /** The value, if any, produced by this parser without consuming more input.
      *
      * @group property
      */
    def nullable: Option[A]

    /** Indicates if there exists a sequence of tokens that the parser can accept.
      *
      * @group property
      */
    def isProductive: Boolean

    /** Returns the set of tokens that are accepted as the next token.
      *
      * @group property
      */
    @inline def first: Set[Kind] = collectFirst(ListSet())

    /** Returns all of kinds that should not be accepted
      * as the next token by a subsequent parser.
      *
      * The value associated to the kind is a parser that accepts
      * all up until that kind.
      *
      * @group property
      */
    @inline def shouldNotFollow: Map[Kind, Parser[Any]] = collectShouldNotFollow(ListSet())

    /** Checks if a `Recursive` parser can be entered without consuming input first.
      *
      * @param rec The `Recursive` parser.
      *
      * @group property
      */
    @inline def calledLeft(rec: Recursive[Any]): Boolean = collectCalledLeft(rec, ListSet())

    /** Checks if this parser corresponds to a LL(1) grammar.
      *
      * @group property
      */
    @inline def isLL1: Boolean = collectIsLL1(ListSet())

    /** Returns all LL(1) conflicts in the parser.
      *
      * @group property
      */
    @inline def conflicts: Set[LL1Conflict] = collectLL1Conflicts(ListSet())

    /** Returns all possible sequences of accepted token kinds in increasing size.
      *
      * @group property
      */
    @inline def trails: Iterator[Trail] = collectTrails(Map.empty).toIterator

    /** Returns a parser that behaves like `this` parser but rejects all tokens whose kind does
      * not satisfy the given predicate.
      *
      * @param predicate The predicate that kinds must satisfy.
      *
      * @group combinator
      */
    @inline def filter(predicate: Kind => Boolean): Parser[A] = collectFilter(predicate, Map.empty)

    /** Returns the set of all kinds that appear somewhere in `this` parser.
      *
      * @group property
      */
    @inline def kinds: Set[Kind] = collectKinds(ListSet())

    def tokensOf(value: Any)(implicit ev: Manifest[Token]): Iterator[Seq[Token]] =
      collectTokens(value, Map.empty).toIterator

    // All the functions below have an argument `recs` which
    // contains the set of all `Recursive` parser on which the call
    // was already performed.
    //
    // This is done to handle the potentially cyclic structure of parsers
    // introduced by `Recursive`.


    /** Collects the nullable value from this parser.
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectNullable(recs: Set[RecId]): Option[A]

    /** Collects the "first" set from this parser.
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectFirst(recs: Set[RecId]): Set[Kind]

    /** Collects the "should-not-follow" set from this parser.
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]]

    /** Checks if the recusive parser `rec` can be invoked without consuming any input tokens.
      *
      * @param rec  The recursive parser.
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean

    /** Checks if this parser is productive.
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectIsProductive(recs: Set[RecId]): Boolean

    /** Checks if this parser is LL(1).
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectIsLL1(recs: Set[RecId]): Boolean

    /** Collects the LL(1) conflicts from this parser.
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict]

    /** Builds a producer of traces from this parser.
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail]

    /** Builds a parser that filter out unwanted kinds.
      *
      * @param predicate Predicate that kinds must satisfy.
      * @param recs      The identifiers of already visited `Recursive` parsers.
      */
    protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[A]

    /** Collects all kinds appearing in this parser.
      *
      * @param recs The identifiers of already visited `Recursive` parsers.
      */
    protected def collectKinds(recs: Set[RecId]): Set[Kind]


    protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
      (implicit ev: Manifest[Token]): Producer[Seq[Token]]

    /** Feeds a token to the parser and obtain a parser for the rest of input.
      *
      * @group parsing
      */
    def derive(token: Token, kind: Kind): Parser[A]


    /** String representation of the parser.
      *
      * @group other
      */
    override def toString = repr(0, Map.empty)

    /** Computes a friendlier string representation for the parser. */
    protected def repr(level: Int, recs: Map[RecId, String]): String


    // Combinators.

    /** Applies a function to the parsed values.
      *
      * @group combinator
      */
    def map[B](function: A => B, inverse: Any => Seq[Any] = (x: Any) => Seq()): Parser[B] with AcceptsInverse[B] =
      this match {
        case Failure => Failure
        case _ => Transform(function, inverse, this)
      }

    /** Sequences `this` and `that` parser. The parsed values are concatenated.
      *
      * @group combinator
      */
    def ++[B](that: Parser[Seq[B]])(implicit ev1: Parser[A] <:< Parser[Seq[B]], ev2: A <:< Seq[B]): Parser[Seq[B]] =
      (this, that) match {
        case (Failure, _) => Failure
        case (_, Failure) => Failure
        case (Success(a), Success(b)) => Success(a ++ b)
        // The next transformation is crucial.
        // It allows to merge together values which accumulate on the left.
        case (_, Concat(left, right)) => (this ++ left) ++ right
        case _ => Concat(this, that)
      }

    /** Sequences `this` and `that` parser. The parsed value from `that` is returned.
      *
      * @group combinator
      */
    def ~>~[B](that: Parser[B])(implicit ev: A <:< Unit): Parser[B] = this.~(that).map(_._2).withInverse {
      case x => scallion.parsing.~((), x)
    }

    /** Sequences `this` and `that` parser. The parsed value from `this` is returned.
      *
      * @group combinator
      */
    def ~<~(that: Parser[Unit]): Parser[A] = this.~(that).map(_._1).withInverse {
      case x => scallion.parsing.~(x, ())
    }

    /** Sequences `this` and `that` parser. The parsed value from `that` is appended to that from `this`.
      *
      * @group combinator
      */
    def :+[B](that: Parser[B])(implicit ev1: Parser[A] <:< Parser[Seq[B]], ev2: A <:< Seq[B]): Parser[Seq[B]] =
      this ++ that.map(Vector(_)).withInverse {
        case Seq(x) => x
      }

    /** Sequences `this` and `that` parser. The parsed value from `that` is prepended to that from `this`.
      *
      * @group combinator
      */
    def +:[B](that: Parser[B])(implicit ev1: Parser[A] <:< Parser[Seq[B]], ev2: A <:< Seq[B]): Parser[Seq[B]] =
      that.map(Vector(_)).withInverse {
        case Seq(x) => x
      } ++ this

    /** Sequences `this` and `that` parser. The parsed values are returned as a pair.
      *
      * @group combinator
      */
    def ~[B](that: Parser[B]): Parser[A ~ B] = (this, that) match {
      case (Failure, _) => Failure
      case (_, Failure) => Failure
      case (Success(a), Success(b)) => Success(scallion.parsing.~(a, b))
      case _ => Sequence(this, that)
    }

    /** Disjunction of `this` and `that` parser.
      *
      * @group combinator
      */
    def |[B >: A](that: Parser[B]): Parser[B] = (this, that) match {
      case (Failure, _) => that
      case (_, Failure) => this
      case _ => Disjunction(this, that)
    }

    /** Disjunction of `this` and `that` parser.
      * The value is tagged to indicate the side which produced it.
      *
      * @group combinator
      */
    def ||[B](that: Parser[B]): Parser[Either[A, B]] =
      this.map(Left(_)).withInverse {
        case Left(x) => x
      } | that.map(Right(_)).withInverse {
        case Right(x) => x
      }

    /** Makes the parser nullable.
      *
      * @group combinator
      */
    def opt: Parser[Option[A]] = this.map(Some(_)).withInverse {
      case Some(x) => x
    } | epsilon(None)


    def void: Parser[Unit] = this.map(_ => ())

    def unit(default: Any): Parser[Unit] = this.map(_ => ()).withInverse {
      case _ => default
    }

    // Parsing.

    /** Consumes a sequence of tokens and parses into a value.
      * When `this` parser is not LL(1), the result is unspecified.
      *
      * @group parsing
      */
    def apply(it: Iterator[Token]): ParseResult[A] = {

      var parser: Parser[A] = this
      while (it.hasNext) {
        val token = it.next()
        val newParser = parser.derive(token, getKind(token))
        if (!newParser.isProductive) {
          return UnexpectedToken(token, parser)
        }
        parser = newParser
      }
      parser.nullable match {
        case None => UnexpectedEnd(parser)
        case Some(value) => Parsed(value, parser)
      }
    }


    // Completions.

    /** Returns all possible completions of `this` parser,
      * ordered by increasing number of tokens.
      *
      * When `this` parser is not LL(1), the result is unspecified.
      *
      * @param toTokens Computes the possible tokens for a given kind.
      *
      * @group complete
      */
    def completions(toTokens: Kind => Seq[Token]): Iterator[Parser[A]] = {

      val kindTokens: Map[Kind, Seq[Token]] =
        kinds.toSeq.map(kind => kind -> toTokens(kind)).toMap

      val unwantedKinds: Set[Kind] =
        kindTokens.filter(_._2.isEmpty).keySet

      val cleanedParser =
        if (unwantedKinds.isEmpty) {
          this
        }
        else {
          this.filter(k => !unwantedKinds.contains(k))
        }

      cleanedParser.trails.flatMap { kinds =>
        val choices = kinds.map(kindTokens)

        def go(elems: Seq[Seq[Token]]): Seq[List[Token]] =
          if (elems.isEmpty) {
            Seq(List())
          }
          else for {
            token <- elems.head
            rest <- go(elems.tail)
          } yield token :: rest

        go(choices).map { tokens =>
          apply(tokens.toIterator).parser
        }
      }
    }

    /** Returns the smallest completion of `this` parser that can
      * be obtained using the partial `toToken` function, if any.
      *
      * When `this` parser is not LL(1), the result is unspecified.
      *
      * @param toToken Computes the preferred token of the given class, if any.
      *
      * @group complete
      */
    def complete(toToken: PartialFunction[Kind, Token]): Parser[A] = {
      val it = completions(kind => toToken.lift(kind).toSeq)
      if (it.hasNext) {
        it.next()
      }
      else {
        Failure
      }
    }
  }

  /** Result of running a `Parser`.
    *
    * @group result
    */
  sealed trait ParseResult[+A] {

    /** Parser for the rest of input. */
    val parser: Parser[A]

    /** Returns the parsed value, if any. */
    def getValue: Option[A] = this match {
      case Parsed(value, _) => Some(value)
      case _ => None
    }
  }

  /** Indicates that the input has been fully processed, resulting in a `value`.
    *
    * A `parser` for subsequent input is also provided.
    *
    * @group result
    */
  case class Parsed[+A](value: A, parser: Parser[A]) extends ParseResult[A]

  /** Indicates that the provided `token` was not expected at that point.
    *
    * The `parser` that rejected the token is returned.
    *
    * @group result
    */
  case class UnexpectedToken[+A](token: Token, parser: Parser[A]) extends ParseResult[A]

  /** Indicates that end of input was unexpectedly encountered.
    *
    * The `parser` for subsequent input is provided.
    *
    * @group result
    */
  case class UnexpectedEnd[+A](parser: Parser[A]) extends ParseResult[A]

  /** Describes a LL(1) conflict.
    *
    * @group conflict
    */
  sealed trait LL1Conflict {

    /** Source of the conflict. */
    val source: Parser[Any]

    /** Parser for a prefix before the conflict occurs. */
    val prefix: Parser[Any]

    private[parsing] def addPrefix(parser: Parser[Any]): LL1Conflict

    /** Returns trails that witness the conflict. */
    def witnesses: Iterator[Trail]
  }

  /** Contains the description of the various LL(1) conflicts.
    *
    * @group conflict
    */
  object LL1Conflict {

    /** Indicates that both branches of a disjunction are nullable. */
    case class NullableConflict(
        prefix: Parser[Any],
        source: Disjunction[Any]) extends LL1Conflict {

      override private[parsing] def addPrefix(start: Parser[Any]): NullableConflict =
        this.copy(prefix = start ~ prefix)

      override def witnesses: Iterator[Trail] = prefix.trails
    }

    /** Indicates that two branches of a disjunction share the same first token(s). */
    case class FirstConflict(
        prefix: Parser[Any],
        ambiguities: Set[Kind],
        source: Disjunction[Any]) extends LL1Conflict {

      override private[parsing] def addPrefix(start: Parser[Any]): FirstConflict =
        this.copy(prefix = start ~ prefix)

      override def witnesses: Iterator[Trail] = for {
        trail <- prefix.trails
        kind <- ambiguities
      } yield trail :+ kind
    }

    /** Indicates that the right end side first token set conflicts with the left end side. */
    case class FollowConflict(
        prefix: Parser[Any],
        ambiguities: Set[Kind],
        source: Parser[Any] with SequenceLike[Any, Any]) extends LL1Conflict {

      override private[parsing] def addPrefix(start: Parser[Any]): FollowConflict =
        this.copy(prefix = start ~ prefix)

      override def witnesses: Iterator[Trail] = for {
        trail <- prefix.trails
        kind <- ambiguities
      } yield trail :+ kind
    }

    /** Indicates that the parser recursively calls itself in a left position. */
    case class LeftRecursiveConflict(
        prefix: Parser[Any],
        source: Recursive[Any]) extends LL1Conflict {

      override private[parsing] def addPrefix(start: Parser[Any]): LeftRecursiveConflict =
        this.copy(prefix = start ~ prefix)

      override def witnesses: Iterator[Trail] = prefix.trails
    }
  }

  import LL1Conflict._

  trait AcceptsInverse[+A] { self: Parser[A] =>
    def withInverse(inverse: PartialFunction[Any, Any]): Parser[A] =
      withInverses((x: Any) => inverse.lift(x).toSeq)

    def withInverses(inverse: Any => Seq[Any]): Parser[A]
  }

  /** Contains primitive basic parsers and parser combinators.
    *
    * @group parser
    */
  object Parser {

    /** Parser that produces `value` without consuming input tokens.
      *
      * @param value The value produced by the parser.
      *
      * @group basic
      */
    case class Success[+A](value: A) extends Parser[A] {

      override val nullable: Option[A] =
        Some(value)

      override val isProductive: Boolean =
        true

      override protected def collectNullable(recs: Set[RecId]): Option[A] =
        Some(value)

      override protected def collectFirst(recs: Set[RecId]): Set[Kind] =
        ListSet()

      override protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]] =
        Map.empty

      override protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean =
        false

      override protected def collectIsLL1(recs: Set[RecId]): Boolean =
        true

      override protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict] =
        ListSet()

      override protected def collectIsProductive(recs: Set[RecId]): Boolean =
        true

      override protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail] =
        Producer.single(Trail.empty)

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[A] =
        this

      override protected def collectKinds(recs: Set[RecId]): Set[Kind] =
        ListSet()

      override protected def collectTokens(other: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] =
        if (value == other) Producer.single(Vector()) else Producer.empty

      override def derive(token: Token, kind: Kind): Parser[A] =
        Failure

      override protected def repr(level: Int, recs: Map[RecId, String]): String =
        "epsilon(" + value.toString + ")"
    }

    /** Parser that produces `value` without consuming input tokens.
      *
      * @group basic
      */
    case object Failure extends Parser[Nothing] with AcceptsInverse[Nothing] {

      override def withInverses(inverse: Any => Seq[Any]): Parser[Nothing] = this

      override val nullable: Option[Nothing] =
        None

      override val isProductive: Boolean =
        false

      override protected def collectNullable(recs: Set[RecId]): Option[Nothing] =
        None

      override protected def collectFirst(recs: Set[RecId]): Set[Kind] =
        ListSet()

      override protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]] =
        Map.empty

      override protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean =
        false

      override protected def collectIsLL1(recs: Set[RecId]): Boolean =
        true

      override protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict] =
        ListSet()

      override protected def collectIsProductive(recs: Set[RecId]): Boolean =
        false

      override protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail] =
        Producer.empty

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[Nothing] =
        this

      override protected def collectKinds(recs: Set[RecId]): Set[Kind] =
        ListSet()

      override protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] =
        Producer.empty

      override def derive(token: Token, kind: Kind): Parser[Nothing] =
        Failure

      override protected def repr(level: Int, recs: Map[RecId, String]): String =
        "failure"
    }

    /** Parser that consumes tokens of the given `kind`.
      *
      * @param kind The kind accepted by the parser.
      *
      * @group basic
      */
    case class Elem(kind: Kind) extends Parser[Token] {

      override val nullable: Option[Token] =
        None

      override val isProductive: Boolean =
        true

      override protected def collectNullable(recs: Set[RecId]): Option[Token] =
        None

      override protected def collectFirst(recs: Set[RecId]): Set[Kind] =
        Set(kind)

      override protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]] =
        Map.empty

      override protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean =
        false

      override protected def collectIsLL1(recs: Set[RecId]): Boolean =
        true

      override protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict] =
        ListSet()

      override protected def collectIsProductive(recs: Set[RecId]): Boolean =
        true

      override protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail] =
        Producer.single(Trail.single(kind))

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[Token] =
        if (predicate(kind)) this else Failure

      override protected def collectKinds(recs: Set[RecId]): Set[Kind] =
        ListSet(kind)

      override protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] =
        ev.unapply(value) match {
          case Some(token) if (getKind(token) == kind) => Producer.single(Vector(token))
          case _ => Producer.empty
        }

      override def derive(token: Token, tokenKind: Kind): Parser[Token] =
        if (tokenKind == kind) Success(token) else Failure

      override protected def repr(level: Int, recs: Map[RecId, String]): String =
        "elem(" + kind + ")"
    }

    /** Unary combinator.
      *
      * @group combinator
      */
    sealed trait Unary[+A] { self: Parser[_] =>

      /** The inner parser.
        *
        * @group subparser
        */
      def inner: Parser[A]
    }

    /** Binary combinator.
      *
      * @group combinator
      */
    sealed trait Binary[+A, +B] { self: Parser[_] =>

      /** The left-hand side parser.
        *
        * @group subparser
        */
      def left: Parser[A]

      /** The right-hand side parser.
        *
        * @group subparser
        */
      def right: Parser[B]
    }

    /** Parser that applies a `function` on the parsed value of the `inner` parser.
      *
      * @param function The function to apply on produced values.
      * @param inner    The inner parser.
      *
      * @group combinator
      */
    case class Transform[A, B](
        function: A => B,
        inverse: Any => Seq[Any],
        inner: Parser[A]) extends Parser[B] with Unary[A] with AcceptsInverse[B] {

      override def withInverses(newInverse: Any => Seq[Any]): Parser[B] =
        this.copy(inverse = newInverse)

      override lazy val nullable: Option[B] =
        inner.nullable.map(function)

      override lazy val isProductive: Boolean =
        inner.isProductive

      override protected def collectNullable(recs: Set[RecId]): Option[B] =
        inner.collectNullable(recs).map(function)

      override protected def collectFirst(recs: Set[RecId]): Set[Kind] =
        inner.collectFirst(recs)

      override protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]] =
        inner.collectShouldNotFollow(recs)

      override protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean =
        inner.collectCalledLeft(rec, recs)

      override protected def collectIsLL1(recs: Set[RecId]): Boolean =
        inner.collectIsLL1(recs)

      override protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict] =
        inner.collectLL1Conflicts(recs)

      override protected def collectIsProductive(recs: Set[RecId]): Boolean =
        inner.collectIsProductive(recs)

      override protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail] =
        inner.collectTrails(recs)

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[B] =
        inner.collectFilter(predicate, recs).map(function, inverse)

      override protected def collectKinds(recs: Set[RecId]): Set[Kind] =
        inner.collectKinds(recs)

      override def derive(token: Token, kind: Kind): Parser[B] =
        inner.derive(token, kind).map(function, inverse)

      override protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] = {

        val producers = inverse(value).map(inversed => inner.collectTokens(inversed, recs))

        if (producers.isEmpty) {
          Producer.empty
        }
        else {
          producers.reduceLeft(tokenSeqOps.union(_, _))
        }
      }

      override protected def repr(level: Int, recs: Map[RecId, String]): String =
        inner.repr(10, recs) + ".map(<function>)"
    }

    /** Parser that sequences the `left` and `right` parsers.
      *
      * @group combinator
      */
    sealed trait SequenceLike[+A, +B] extends Binary[A, B] { self: Parser[Any] =>

      override protected def collectFirst(recs: Set[RecId]): Set[Kind] =
        left.nullable match {
          case Some(_) => left.collectFirst(recs) ++ right.collectFirst(recs)
          case None => left.collectFirst(recs)
        }

      override protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]] = {
        val rightSNF =
          right.collectShouldNotFollow(recs).map {
            case (k, v) => k -> left ~ v
          }

        right.nullable match {
          case Some(_) => combineSNF(
            left.collectShouldNotFollow(recs),
            rightSNF
          )
          case None => rightSNF
        }
      }

      override protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean =
        left.collectCalledLeft(rec, recs) || (left.nullable.nonEmpty && right.collectCalledLeft(rec, recs))

      override protected def collectIsLL1(recs: Set[RecId]): Boolean =
        left.collectIsLL1(recs) && right.collectIsLL1(recs) &&
        (left.shouldNotFollow.keySet & right.first).isEmpty

      override protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict] = {

        val leftSNF = left.shouldNotFollow

        val problematicKinds = (leftSNF.keySet & right.first)

        val followConflicts: Set[LL1Conflict] =
          if (problematicKinds.isEmpty) {
            ListSet()
          }
          else {
            problematicKinds.map { kind =>
              FollowConflict(leftSNF(kind), problematicKinds, this)
            }
          }

        val baseConflicts: Set[LL1Conflict] =
          left.collectLL1Conflicts(recs) union
          right.collectLL1Conflicts(recs).map(_.addPrefix(left))

        baseConflicts union followConflicts
      }

      override protected def collectIsProductive(recs: Set[RecId]): Boolean =
        left.collectIsProductive(recs) && right.collectIsProductive(recs)

      override protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail] =
        trailOps.product(left.collectTrails(recs), right.collectTrails(recs))

      override protected def collectKinds(recs: Set[RecId]): Set[Kind] =
        left.collectKinds(recs) union right.collectKinds(recs)
    }

    /** Parser that sequences the `left` and `right` parsers and groups the results.
      *
      * @param left  The parser for the prefix.
      * @param right The parser for the suffix.
      *
      * @group combinator
      */
    case class Sequence[+A, +B](left: Parser[A], right: Parser[B])
        extends Parser[A ~ B] with SequenceLike[A, B] {

      override lazy val isProductive: Boolean =
        left.isProductive && right.isProductive

      override lazy val nullable: Option[A ~ B] = for {
        leftValue <- left.nullable
        rightValue <- right.nullable
      } yield scallion.parsing.~(leftValue, rightValue)

      override protected def collectNullable(recs: Set[RecId]): Option[A ~ B] = for {
        leftValue <- left.collectNullable(recs)
        rightValue <- right.collectNullable(recs)
      } yield scallion.parsing.~(leftValue, rightValue)

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[A ~ B] =
        left.collectFilter(predicate, recs) ~ right.collectFilter(predicate, recs)

      override protected def repr(level: Int, recs: Map[RecId, String]): String = {
        val l = left.repr(9, recs)
        val r = right.repr(10, recs)

        if (level > 9) {
          "(" + l + " ~ " + r + ")"
        }
        else {
          l + " ~ " + r
        }
      }

      override protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] =

        value match {
          case a ~ b => tokenSeqOps.product(left.collectTokens(a, recs), right.collectTokens(b, recs))
          case _ => Producer.empty
        }

      override def derive(token: Token, kind: Kind): Parser[A ~ B] = {
        val derived = left.derive(token, kind)

        if (!derived.isProductive) {
          left.nullable match {
            case Some(leftValue) => Success(leftValue) ~ right.derive(token, kind)
            case None => Failure
          }
        }
        else {
          derived ~ right
        }
      }
    }

    /** Parser that sequences the `left` and `right` parsers and concatenates the results.
      *
      * @param left  The parser for the prefix.
      * @param right The parser for the suffix.
      *
      * @group combinator
      */
    case class Concat[+A](left: Parser[Seq[A]], right: Parser[Seq[A]])
        extends Parser[Seq[A]] with SequenceLike[Seq[A], Seq[A]] {

      override lazy val isProductive: Boolean =
        left.isProductive && right.isProductive

      override lazy val nullable: Option[Seq[A]] = for {
        leftValue <- left.nullable
        rightValue <- right.nullable
      } yield leftValue ++ rightValue

      override protected def collectNullable(recs: Set[RecId]): Option[Seq[A]] = for {
        leftValue <- left.collectNullable(recs)
        rightValue <- right.collectNullable(recs)
      } yield leftValue ++ rightValue

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[Seq[A]] =
        left.collectFilter(predicate, recs) ++ right.collectFilter(predicate, recs)

      override protected def repr(level: Int, recs: Map[RecId, String]): String = {
        val l = left.repr(7, recs)
        val r = right.repr(8, recs)

        if (level > 7) {
          "(" + l + " ++ " + r + ")"
        }
        else {
          l + " ++ " + r
        }
      }

      override def derive(token: Token, kind: Kind): Parser[Seq[A]] = {
        val derived = left.derive(token, kind)

        if (!derived.isProductive) {
          left.nullable match {
            case Some(leftValue) => Success(leftValue) ++ right.derive(token, kind)
            case None => Failure
          }
        }
        else {
          derived ++ right
        }
      }

      override protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] =

        value match {
          case Seq(xs @ _*) => {
            val producers = for {
              i <- 0 to xs.size
              (a, b) = xs.splitAt(i)
            } yield tokenSeqOps.product(left.collectTokens(a, recs), right.collectTokens(b, recs))

            if (producers.isEmpty) {
              Producer.empty
            }
            else {
              producers.reduceLeft(tokenSeqOps.union(_, _))
            }
          }
          case _ => Producer.empty
        }
    }

    /** Parser that acts either as the disjunction of the `left` and `right` parsers.
      *
      * @param left  The parser for the first alternative.
      * @param right The parser for the second alternative.
      *
      * @group combinator
      */
    case class Disjunction[+A](left: Parser[A], right: Parser[A]) extends Parser[A] with Binary[A, A] {

      private lazy val order = if (right.nullable.nonEmpty) (left, right) else (right, left)
      private lazy val firstFirst = order._1.first

      override lazy val nullable: Option[A] =
        left.nullable orElse right.nullable

      override lazy val isProductive: Boolean =
        left.isProductive || right.isProductive

      override protected def collectNullable(recs: Set[RecId]): Option[A] =
        left.collectNullable(recs) orElse right.collectNullable(recs)

      override protected def collectFirst(recs: Set[RecId]): Set[Kind] =
        left.collectFirst(recs) ++ right.collectFirst(recs)

      override protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]] = {
        val fromLeft: Map[Kind, Parser[Any]] =
          if (right.nullable.nonEmpty) {
            left.first.toSeq.map {
              kind => kind -> Success(())
            }.toMap
          }
          else {
            Map.empty
          }
        val fromRight: Map[Kind, Parser[Any]] =
          if (left.nullable.nonEmpty) {
            right.first.toSeq.map {
              kind => kind -> Success(())
            }.toMap
          }
          else {
            Map.empty
          }

        val baseSNF = combineSNF(left.collectShouldNotFollow(recs), right.collectShouldNotFollow(recs))
        val addedSNF = combineSNF(fromLeft, fromRight)

        combineSNF(baseSNF, addedSNF)
      }

      override protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean =
        left.collectCalledLeft(rec, recs) || right.collectCalledLeft(rec, recs)

      override protected def collectIsLL1(recs: Set[RecId]): Boolean =
        left.collectIsLL1(recs) && right.collectIsLL1(recs) &&
        (left.nullable.isEmpty || right.nullable.isEmpty) &&
        (left.first & right.first).isEmpty

      override protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict] = {

        val problematicKinds = (left.first & right.first)

        val firstConflicts: Set[LL1Conflict] =
          if (problematicKinds.isEmpty) {
            ListSet()
          }
          else {
            ListSet(FirstConflict(Success(()), problematicKinds, this))
          }

        val nullableConflicts: Set[LL1Conflict] =
          if (left.nullable.isEmpty || right.nullable.isEmpty) {
            ListSet()
          }
          else {
            ListSet(NullableConflict(Success(()), this))
          }

        val baseConflicts: Set[LL1Conflict] =
          left.collectLL1Conflicts(recs) union right.collectLL1Conflicts(recs)

        baseConflicts union firstConflicts union nullableConflicts
      }

      override protected def collectIsProductive(recs: Set[RecId]): Boolean =
        left.collectIsProductive(recs) || right.collectIsProductive(recs)

      override protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail] =
        trailOps.union(left.collectTrails(recs), right.collectTrails(recs))

      override def derive(token: Token, kind: Kind): Parser[A] = {
        if (firstFirst.contains(kind)) {
          order._1.derive(token, kind)
        }
        else {
          order._2.derive(token, kind)
        }
      }

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[A] =
        left.collectFilter(predicate, recs) | right.collectFilter(predicate, recs)

      override protected def collectKinds(recs: Set[RecId]): Set[Kind] =
        left.collectKinds(recs) union right.collectKinds(recs)

      override protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] =
        tokenSeqOps.union(left.collectTokens(value, recs), right.collectTokens(value, recs))

      override protected def repr(level: Int, recs: Map[RecId, String]): String = {
        val l = left.repr(1, recs)
        val r = right.repr(2, recs)

        if (level > 1) {
          "(" + l + " | " + r + ")"
        }
        else {
          l + " | " + r
        }
      }
    }

    /** Identifier for Recursive. */
    protected type RecId = Int

    /** Companion object of `Recursive`.
      *
      * @group combinator
      */
    object Recursive {
      private var freeNextId: RecId = 0

      /** Generates a fresh identifier. */
      private def nextId(): RecId = synchronized {
        val res = freeNextId
        freeNextId += 1
        res
      }

      /** Extract the inner parser of a `Recursive` parser. */
      def unapply[A](that: Parser[A]): Option[Parser[A]] = {
        if (that.isInstanceOf[Recursive[_]]) {
          Some(that.asInstanceOf[Recursive[A]].inner)
        }
        else {
          None
        }
      }

      /** Creates a new `Recursive` parser.
        *
        * @param parser The inner parser.
        */
      def create[A](parser: => Parser[A]): Recursive[A] = new Recursive[A] {
        override protected val id = nextId()
        override lazy val inner: Parser[A] = parser
      }
    }

    /** Parser that may recursively call itself.
      *
      * @group combinator
      */
    sealed abstract class Recursive[+A] extends Parser[A] with Unary[A] {

      /** The inner parser.
        *
        * @group subparser
        */
      def inner: Parser[A]

      /** Unique identifier for this recursive parser. */
      protected val id: RecId

      /** Checks if `this` is equal to `other`.
        *
        * @group other
        */
      override def equals(other: Any): Boolean =
        if (!other.isInstanceOf[Recursive[_]]) {
          false
        }
        else {
          val that = other.asInstanceOf[Recursive[_]]
          this.id == that.id
        }

      /** Returns the hash of this object.
        *
        * @group other
        */
      override def hashCode(): Int = id

      override lazy val nullable: Option[A] =
        inner.collectNullable(Set(this.id))

      override lazy val isProductive: Boolean =
        inner.collectIsProductive(Set(this.id))

      override protected def collectNullable(recs: Set[RecId]): Option[A] =
        if (recs.contains(this.id)) None else inner.collectNullable(recs + this.id)

      override protected def collectFirst(recs: Set[RecId]): Set[Kind] =
        if (recs.contains(this.id)) ListSet() else inner.collectFirst(recs + this.id)

      override protected def collectShouldNotFollow(recs: Set[RecId]): Map[Kind, Parser[Any]] =
        if (recs.contains(this.id)) Map.empty else inner.collectShouldNotFollow(recs + this.id)

      override protected def collectCalledLeft(rec: Recursive[_], recs: Set[RecId]): Boolean =
        if (recs.contains(this.id)) false else (this.id == rec.id) || inner.collectCalledLeft(rec, recs + this.id)

      override protected def collectIsLL1(recs: Set[RecId]): Boolean =
        if (recs.contains(this.id)) true else !inner.calledLeft(this) && inner.collectIsLL1(recs + this.id)

      override protected def collectLL1Conflicts(recs: Set[RecId]): Set[LL1Conflict] =
        if (recs.contains(this.id)) ListSet() else {
          val base = inner.collectLL1Conflicts(recs + this.id)

          if (inner.calledLeft(this)) {
            base + LeftRecursiveConflict(Success(()), this)
          }
          else {
            base
          }
        }

      override protected def collectIsProductive(recs: Set[RecId]): Boolean =
        if (recs.contains(this.id)) false else inner.collectIsProductive(recs + this.id)

      override def derive(token: Token, kind: Kind): Parser[A] =
        inner.derive(token, kind)

      override protected def collectTrails(recs: Map[RecId, () => Producer[Trail]]): Producer[Trail] =
        recs.get(this.id) match {
          case None => {
            lazy val pair: (Producer[Trail], () => Producer[Trail]) =
              Producer.duplicate(Producer.lazily {
                inner.collectTrails(recs + (this.id -> pair._2))
              })
            pair._1
          }
          case Some(createProducer) => createProducer()
        }

      override protected def collectTokens(value: Any, recs: Map[(RecId, Any), () => Producer[Seq[Token]]])
          (implicit ev: Manifest[Token]): Producer[Seq[Token]] =

        recs.get((this.id, value)) match {
          case None => {
            lazy val pair: (Producer[Seq[Token]], () => Producer[Seq[Token]]) =
              Producer.duplicate(Producer.lazily {
                inner.collectTokens(value, recs + ((this.id, value) -> pair._2))
              })
            pair._1
          }
          case Some(createProducer) => createProducer()
        }

      override protected def collectFilter(predicate: Kind => Boolean, recs: Map[RecId, Parser[Any]]): Parser[A] = {
        recs.get(this.id) match {
          case None => {
            lazy val rec: Parser[A] = recursive(inner.collectFilter(predicate, recs + (this.id -> rec)))
            rec
          }
          case Some(rec) => rec.asInstanceOf[Parser[A]]
        }
      }

      override protected def collectKinds(recs: Set[RecId]): Set[Kind] =
        if (recs.contains(this.id)) ListSet() else inner.collectKinds(recs + this.id)

      override protected def repr(level: Int, recs: Map[RecId, String]): String = {
        recs.get(this.id) match {
          case None => {
            val n = (recs.size + 1).toString
            "recursive<" + n + ">(" + inner.repr(0, recs + (this.id -> n)) + ")"
          }
          case Some(n) => "<" + n + ">"
        }
      }
    }

    /** Combines two maps by applying a function
      * in case of conflicting entries.
      */
    private def combine[K, V](merge: (V, V) => V)(left: Map[K, V], right: Map[K, V]): Map[K, V] =
      right.foldLeft(left) {
        case (acc, (key, value)) => acc + (key -> left.get(key).map(merge(_, value)).getOrElse(value))
      }

    /** Combines two Should-Not-Follow results by taking
      * the disjunction of parser in case of conflicting entries.
      */
    private def combineSNF(
        left: Map[Kind, Parser[Any]],
        right: Map[Kind, Parser[Any]]): Map[Kind, Parser[Any]] =
      combine((p1: Parser[Any], p2: Parser[Any]) => p1 | p2)(left, right)
  }


  // API for combinators and basic parsers.

  /** Parser that accepts tokens of the provided `kind`.
    *
    * @group basic
    */
  def elem(kind: Kind): Parser[Token] = Elem(kind)

  /** Parser that accepts tokens of the provided `kind`.
    * A function directly is applied on the successfully matched token.
    *
    * @group basic
    */
  def accept[A](kind: Kind)(function: PartialFunction[Token, A]): Parser[A] with AcceptsInverse[A] =
    elem(kind).map(function)

  /** Indicates that the parser can be recursively invoke itself.
    *
    * @group combinator
    */
  def recursive[A](parser: => Parser[A]): Parser[A] = Recursive.create(parser)

  /** Parser that produces the given `value` without consuming any input.
    *
    * @group basic
    */
  def epsilon[A](value: A): Parser[A] = Success(value)

  /** Parser that always fails.
    *
    * @group basic
    */
  def failure[A]: Parser[A] = Failure

  /** Parser that represents 0 or 1 instances of the `parser`.
    *
    * @group combinator
    */
  def opt[A](parser: Parser[A]): Parser[Option[A]] = parser.opt

  /** Parser that represents 0 or more repetitions of the `rep` parser.
    *
    * @group combinator
    */
  def many[A](rep: Parser[A]): Parser[Seq[A]] = {
    lazy val rest: Parser[Seq[A]] = recursive(rep +: rest | epsilon(Vector()))
    rest
  }

  /** Parser that represents 1 or more repetitions of the `rep` parser.
    *
    * @group combinator
    */
  def many1[A](rep: Parser[A]): Parser[Seq[A]] = rep +: many(rep)

  /** Parser that represents 0 or more repetitions of the `rep` parser, separated by `sep`.
    *
    * @group combinator
    */
  def repsep[A](rep: Parser[A], sep: Parser[Unit]): Parser[Seq[A]] = rep1sep(rep, sep) | epsilon(Vector())

  /** Parser that represents 1 or more repetitions of the `rep` parser, separated by `sep`.
    *
    * @group combinator
    */
  def rep1sep[A](rep: Parser[A], sep: Parser[Unit]): Parser[Seq[A]] = {
    lazy val rest: Parser[Seq[A]] = recursive((sep ~>~ rep) +: rest | epsilon(Vector()))
    rep +: rest
  }

  /** Parser that represents the disjunction of several `parsers`.
    *
    * @group combinator
    */
  def oneOf[A](parsers: Parser[A]*): Parser[A] = {
    var queue = parsers.toVector :+ failure[A]

    while (queue.size > 1) {
      val a = queue(0)
      val b = queue(1)
      queue = queue.drop(2)
      queue :+= a | b
    }

    queue.head
  }
}
