package dbstage2
package example

import squid.utils._

case class PersonId(value: Int) extends AnyVal with Field[Int]
case class Name(value: String) extends AnyVal with Field[String]
case class Age(value: Int) extends AnyVal with Field[Int]
case class Gender(value: Bool) extends AnyVal with Field[Bool]
case class Address(value: String) extends AnyVal with Field[String]

case class JobId(value: Int) extends AnyVal with Field[Int]
case class JobTitle(value: String) extends AnyVal with Field[String]
case class Salary(value: Int) extends AnyVal with Field[Int]

object RecordTestsDefs {
  
  type Person = Name :: Age :: Gender :: Address :: NoFields
  type IdPerson = PersonId :: Person
  type Job = JobTitle :: Salary :: Address :: NoFields
  type IdJob = JobId :: Job
  type HasJob = PersonId :: JobId :: NoFields
  
  val p = Name("Jo") :: Age(42) :: Gender(true) :: Address("DTC") :: NoFields
  identity[Person](p)
  
  val j = JobTitle("Researcher") :: Salary(420) :: Address("NYC") :: NoFields
  identity[Job](j)
  
  val recordSet = p :: j :: NoFields
  assert(p == recordSet[Person])
  assert(j == recordSet[Job])
  
}

object RecordTests extends App {
  import RecordTestsDefs._
  import Embedding.Predef._
  import Embedding.Quasicodes._
  
  println(p,p[Name])
  println(j,j[JobTitle])
  
  // FIXME:
  //val P2 = new Variable[Int]; code"$P2+1" // Cannot use variable of non-singleton type dbstage.Embedding.Variable[Int]
  // FIXME when used in Quasicode, always infers Nothing context!
  
{
  val P = Relation[Person]()
    .withPrimaryKeys[Name :: Address :: NoFields]
    .withAutoGeneratedKey[PersonId]
  
  val J = Relation[Job]()
    .withPrimaryKey[JobTitle]
    .withAutoGeneratedKey[JobId]
  
  val HasJob = Relation[PersonId :: JobId :: NoFields]()
    .withForeighKey[PersonId](P)
    .withForeighKey[JobId](J)
  
  println(P)
  println(J)
  println(HasJob)
  
  println(P.primaryKeys)
  
  //P.compileQuery(p => code{
  //  $(p).filter(_ => true)
  //})
  //P.compileQuery(p => code{
  //  P.filter(_ => true)
  //})
  
  val db = Database()
    .withRelation(P)
    .withRelation(J)
    .withRelation(HasJob)
    
  //val P2 = new Variable[Int]
  
  //db.compile(dbg_code{
  //  //$(P).filter(_ => true)
  //  $(P2)+1
  //})
  //db.compile(code"$P2+1")
  //db.compile(code"$P.columnStore")
  
  db.compile(code"""
    $P.query.filter(p => p[Age] > 18)
  """) alsoApply println
  
  
}
  println("Done.")
}