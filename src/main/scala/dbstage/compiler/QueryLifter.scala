package dbstage
package compiler

import query._
import squid.utils._
import cats.Monoid
import cats.Semigroup
import cats.kernel.CommutativeMonoid
import cats.kernel.CommutativeSemigroup
import Embedding.Predef._
import dbstage.Embedding.HollowedCode

import scala.language.higherKinds

/*

depending on purity of nested query, accept or not extraction under uncertain eval context

*/

class QueryLifter {
  
  def apply[A:CodeType,C](q: Code[A,C]): Option[LiftedQuery[A,C]] = liftQuery(q) match {
    case lq: LiftedQuery[A,C] => Some(lq)
    case uq: UnliftedQuery[A,C] => None
  }
  
  def liftQuery[T:CodeType,C](q: Code[T,C]): QueryRepr[T,C] = {
    println(s"\n<<-- Rec ${q|>showC}\n")
    object Ins extends Embedding.Inspector[T,C,LiftedQuery[T,C]] {
      def traverse[S:CodeType]: PartialFunction[Code[S,C], HollowedCode[T,S,C] => LiftedQuery[T,C]] = {
        
        case cde @ code"readInt" => // Just a dummy case to test the nested-lifting 
          h => NestedQuery(h.v,MonoidEmpty(code"$cde:S"))(liftQuery(h.body))
          
        //case cde @ code"NonEmptyOrderedOps[$ast,$at]($as)($aord,$ane,$afin).map[S]($v => $body)($tsem)" =>
        case cde @ code"NonEmptyOrderedOps[$ast,$at]($as)($aord,$ane,$afin).map[S]($f)($tsem)" =>
          val res = liftSemigroup(tsem) match {
            case Right(lmon) =>
              val lifted = liftProductions[S,Option[S],C,C](cde,lmon,code"(x:S)=>Some(x)")(prods => Comprehension(prods,lmon))
              val w = new Variable[Option[S]]()
              val res = NestedQuery(w,lifted)(UnliftedQuery(code"$w.get"))
              //println(res)
              res
            case Left(lmon) =>
              die // TODO
          }
          h => NestedQuery[T,S,C](h.v,res)(liftQuery(h.body))
          
        case code"OrderedOps[$ast,$at]($as)($aord,$afin).map[$rt,T]($v => $body)($into,$mmon)" =>
          ???
      }
    }
    
    Ins(q) match {
      case Left(cde) => UnliftedQuery(cde)
      case Right(lq) =>
        //println(lq)
        //die
        lq
    }
  }
  /*
  //abstract class LiftedQueryK[A,R,C,F[_]] {
  abstract class LiftedQueryK[R,E,F[_]] {
    def apply[D<:E](x: F[D]): LiftedQuery[R,D] = impl(x)
    def impl[D<:E]: F[D] => LiftedQuery[R,D
  }
  abstract class ProductionsK[R,E] extends LiftedQueryK[R,E,Productions[R,?]]
  abstract class StagedDataSourceK[A,R,C] extends LiftedQueryK[R,C,StagedDataSource[A,?]]
  */
  
  def liftProductions[A:CodeType,R:CodeType,C<:E,E]
  (q: Code[A,C], lmon: StagedMonoid[R,C], f: Code[A=>R,C])(k: Productions[R,C] => LiftedQuery[R,E]): LiftedQuery[R,E]
  = q match {
    case code"NonEmptyOrderedOps[$ast,$at]($as)($aord,$ane,$afin).map[A]($v => $body)($tsem)" =>
      liftSemigroup(tsem) match {
        case Right(lmon2) =>
          if (lmon == lmon2)
            liftDataSource[at.Typ,ast.Typ,C,E,R](as,afin)(ds =>
              liftProductions[A,R,C&v.Ctx,E](body, lmon, f)({prods =>
                k(Iteration(ds,v)(prods))
                //k(Iteration[at.Typ,R,C](ds,v)(prods))
              })
            )
          else lastWords(s"TODO: different monoid $lmon and $lmon2")
        case Left(lmon) =>
          die // TODO
      }
    case code"OrderedOps[$ast,$at]($as)($aord,$afin).map[$rt,A]($v => $body)($into,$mmon)" =>
      ???
    case r =>
      println(s"YIELD:\n${indentString(r|>showC)}")
      k(Yield(liftQuery(code"true"), liftQuery(
        f(q)
      )))
  }
  
  def liftDataSource[A:CodeType,As:CodeType,C<:E,E,R:CodeType]
  (cde: Code[As,C], srcEv: Code[As SourceOf A,C])(k: StagedDataSource[A,C] => LiftedQuery[R,E]): LiftedQuery[R,E]
  = cde match {
    case code"if ($cond) $thn else $els : As" =>
      liftDataSource[A,As,C,E,R](thn,srcEv)(thn => liftDataSource[A,As,C,E,R](els,srcEv)(els => MonoidMerge(k(thn),k(els))))
    case cde =>
      println(s"SOURCE:\n${indentString(cde|>showC)}")
      k(StagedDataSourceOf(liftQuery(cde),srcEv))
  }
  
  def liftMonoid[S:CodeType,C](cde: Code[Monoid[S],C]): StagedMonoid[S,C] = ???
  //def liftSemigroup[S:CodeType,C](cde: Code[Semigroup[S],C]): StagedMonoid[S,C] =
  //  //new StagedMonoid[S,C](false,false){} // TODO
  //  RawStagedSemigroup[S,C](cde,false,false).asInstanceOf[StagedMonoid[S,C]] // FIXME
  def liftSemigroup[S:CodeType,C](cde: Code[Semigroup[S],C]): Either[StagedMonoid[S,C],StagedMonoid[Option[S],C]] = cde match {
    case cde =>
      Right(RawStagedSemigroup[S,C](cde,false,false))
  }
  
}
object QueryLifter extends QueryLifter
