package dbstage

import scala.annotation.implicitNotFound
import squid.utils._
import cats.Monoid
import cats.syntax.all._
import squid.lib.transparencyPropagating
import squid.quasi.{embed,phase}

case class ~[A,B](lhs: A, rhs: B) {
  override def toString: String = rhs match {
    case ~(a,b) => s"$lhs ~ ($rhs)"
    case _ => s"$lhs ~ $rhs"
  }
}
object ~ {
  @transparencyPropagating
  implicit def monoid[A:Monoid,B:Monoid]: Monoid[A ~ B] =
    Monoid.instance(Monoid[A].empty ~ Monoid[B].empty)((x,y) => (x.lhs |+| y.lhs) ~ (x.rhs |+| y.rhs))
}

final class RecordSyntax[A](private val self: A) extends AnyVal {
  def ~ [B] (that: B) = new ~(self,that)
  def select[B](implicit ev: A CanAccess B): B = ev(self)
  // TODO: a selectFirst that is left-biased; use in apply? -- and a selectLast; reformulate RecordAccess in terms of these two?
  def project[B](implicit ev: A ProjectsOn B): B = ev(self)
  //def apply[F](implicit access: CanAccess[A,F]): F#Typ = access(self).value
  def apply[F](implicit accessF: CanAccess[A,F], ws: WrapsBase[F]): ws.Typ = apply(ws.instance)
  def apply[F,V](w: F Wraps V)(implicit accessF: A CanAccess F): V = accessF(self) |> w.deapply
}

@implicitNotFound("Type ${A} is not known to be accessible in ${R}")
case class CanAccess[A,R](fun: A => R) extends (A => R) { def apply(a: A) = fun(a) } // TODO rem indirection; make abstract class?
@embed
object CanAccess {
  @phase('Sugar)
  implicit def fromLHS[A,B,T](implicit ev: A CanAccess T): (A ~ B) CanAccess T = CanAccess(ev compose (_.lhs))
  @phase('Sugar)
  implicit def fromRHS[A,B,T](implicit ev: B CanAccess T): (A ~ B) CanAccess T = CanAccess(ev compose (_.rhs))
  @phase('Sugar)
  implicit def fromT[T]: T CanAccess T = CanAccess(identity)
}

@implicitNotFound("Type ${A} cannot be projected onto type ${B}")
case class ProjectsOn[A,B](fun: A => B) extends (A => B) { def apply(a: A) = fun(a) }
object ProjectsOn extends ProjectLowPrio {
  //implicit object projectUnit extends Project[Any,Unit](_ => ())  // TODO make Project contravariant?
  implicit def projectUnit[T] = ProjectsOn[T,Unit](_ => ())
  implicit def projectLHS[A,B,T](implicit ev: A CanAccess T): (A ~ B) ProjectsOn T = ProjectsOn(ev compose (_.lhs))
  implicit def projectRHS[A,B,T](implicit ev: B CanAccess T): (A ~ B) ProjectsOn T = ProjectsOn(ev compose (_.rhs))
  //implicit def projectBoth[A,B,T](implicit evLHS: T Project A, evRHS: T Project B): T Project (A ~ B) = Project(t => evLHS(t) ~ evRHS(t))
}
class ProjectLowPrio extends ProjectLowPrio2 {
  implicit def projectBoth[A,B,T](implicit evLHS: T ProjectsOn A, evRHS: T ProjectsOn B): T ProjectsOn (A ~ B) =
    ProjectsOn(t => evLHS(t) ~ evRHS(t))
}
class ProjectLowPrio2 {
  implicit def projectT[T]: T ProjectsOn T = ProjectsOn(identity)
}
