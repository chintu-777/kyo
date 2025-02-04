package kyo2

import Maybe.*
import Maybe.internal.*

opaque type Maybe[+A] >: (Empty | Defined[A]) = Empty | Defined[A]

object Maybe:
    given [A, B](using CanEqual[A, B]): CanEqual[Maybe[A], Maybe[B]] = CanEqual.derived
    given [A]: Conversion[Maybe[A], IterableOnce[A]]                 = _.iterator

    def apply[A](v: A): Maybe[A] =
        if isNull(v) then
            Empty
        else
            v match
                case v: DefinedEmpty => v.nest
                case v               => v

    def fromOption[A](opt: Option[A]): Maybe[A] =
        opt match
            case Some(v) => Defined(v)
            case None    => Empty

    def empty[A]: Maybe[A] = Empty

    def when[A](cond: Boolean)(v: => A): Maybe[A] =
        if cond then v else Empty

    opaque type Defined[+A] = A | DefinedEmpty

    object Defined:

        def apply[A](v: A): Defined[A] =
            v match
                case v: DefinedEmpty => v.nest
                case v: Empty        => DefinedEmpty.one
                case v               => v

        // TODO avoid allocation
        def unapply[A](opt: Maybe[A]): Option[A] =
            opt.toOption
    end Defined

    sealed abstract class Empty
    case object Empty extends Empty

    extension [A](self: Maybe[A])

        inline def toOption: Option[A] =
            if isEmpty then None
            else Some(get)

        inline def isEmpty: Boolean =
            self match
                case _: Empty => true
                case _        => false

        inline def isDefined: Boolean = !isEmpty

        inline def nonEmpty: Boolean = !isEmpty

        // TODO compilation failure if inlined
        def get: A =
            (self: @unchecked) match
                case _: Empty =>
                    throw new NoSuchElementException("Maybe.get")
                case self: DefinedEmpty =>
                    self.unnest.asInstanceOf[A]
                case v: A @unchecked =>
                    v

        inline def getOrElse[B >: A](inline default: => B): B =
            if isEmpty then default else get

        inline def fold[B](inline ifEmpty: => B)(inline ifDefined: A => B): B =
            if isEmpty then ifEmpty else ifDefined(get)

        inline def map[B](inline f: A => B): Maybe[B] =
            if isEmpty then Empty else f(get)

        inline def flatMap[B](inline f: A => Maybe[B]): Maybe[B] =
            if isEmpty then Maybe.empty else f(get)

        inline def flatten[B](using inline ev: A <:< Maybe[B]): Maybe[B] =
            if isEmpty then Empty else ev(get)

        inline def filter(inline f: A => Boolean): Maybe[A] =
            if isEmpty || f(get) then self else Empty

        inline def filterNot(inline f: A => Boolean): Maybe[A] =
            if isEmpty || !f(get) then self else Empty

        // TODO compilation failure if inlined
        def contains[B](elem: B)(using CanEqual[A, B]): Boolean =
            !isEmpty && get == elem

        inline def exists(inline f: A => Boolean): Boolean =
            !isEmpty && f(get)

        inline def forall(inline f: A => Boolean): Boolean =
            isEmpty || f(get)

        inline def foreach(inline f: A => Unit): Unit =
            if !isEmpty then f(get)

        inline def collect[B](inline pf: PartialFunction[A, B]): Maybe[B] =
            if !isEmpty then
                val value = get
                if pf.isDefinedAt(value) then
                    pf(value)
                else
                    Empty
                end if
            else Empty

        inline def orElse[B >: A](inline alternative: => Maybe[B]): Maybe[B] =
            if isEmpty then alternative else self

        def zip[B](that: Maybe[B]): Maybe[(A, B)] =
            if isEmpty || that.isEmpty then Empty else (get, that.get)

        inline def iterator: Iterator[A] =
            if isEmpty then collection.Iterator.empty else collection.Iterator.single(get)

        inline def toList: List[A] =
            if isEmpty then List.empty else get :: Nil

        inline def toRight[X](inline left: => X): Either[X, A] =
            if isEmpty then Left(left) else Right(get)

        inline def toLeft[X](inline right: => X): Either[A, X] =
            if isEmpty then Right(right) else Left(get)

    end extension

    private[kyo2] object internal:

        case class DefinedEmpty(val depth: Int):
            def unnest =
                if depth > 1 then
                    DefinedEmpty(depth - 1)
                else
                    Empty
            def nest =
                DefinedEmpty(depth + 1)
        end DefinedEmpty

        object DefinedEmpty:
            val cache = (0 until 100).map(new DefinedEmpty(_)).toArray
            val one   = DefinedEmpty(1)
            def apply(depth: Int): DefinedEmpty =
                if depth < cache.length then
                    cache(depth)
                else
                    new DefinedEmpty(depth)
        end DefinedEmpty
    end internal
end Maybe
