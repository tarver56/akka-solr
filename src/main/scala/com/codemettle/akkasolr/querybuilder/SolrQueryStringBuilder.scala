/*
 * SolrQueryStringBuilder.scala
 *
 * Updated: Oct 14, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasolr.querybuilder

import com.codemettle.akkasolr.Solr

import akka.actor.ActorRefFactory

/**
 * @author steven
 *
 */
object SolrQueryStringBuilder {
    type FieldValueType = Any

    sealed trait QueryPart {
        def queryOptions()(implicit arf: ActorRefFactory) = SolrQueryBuilder(this)

        private def andOrRender(parts: Seq[QueryPart], joiner: String)(implicit arf: ActorRefFactory) = {
            val nonEmpty = parts filterNot (_ eq Empty)
            if (nonEmpty.isEmpty)
                ""
            else if (nonEmpty.size == 1)
                nonEmpty.head.render
            else
                nonEmpty map (_.render) mkString ("(", joiner, ")")
        }

        private def notRender(notWhat: QueryPart)(implicit arf: ActorRefFactory) = {
            val notStr = notWhat.render
            if (notStr.isEmpty) "" else s"-$notStr"
        }

        def render(implicit arf: ActorRefFactory): String = this.simplify match {
            case Empty ⇒ ""
            case RawQuery(q) ⇒ q
            case FieldValue(f, v) ⇒ f.fold(valueEsc(v))(fn ⇒ s"$fn:${valueEsc(v)}")
            case Not(q) ⇒ notRender(q)
            case Range(f, l, u) ⇒ f.fold(s"[$l TO $u]")(fn ⇒ s"$fn:[$l TO $u]")
            case OrQuery(parts) ⇒ andOrRender(parts, " OR ")
            case AndQuery(parts) ⇒ andOrRender(parts, " AND ")
            case IsAnyOf(field, values) ⇒
                def valsToOr(vals: Iterable[FieldValueType]) = {
                    val prefix = field.fold("")(f ⇒ s"$f:")
                    vals.map(valueEsc).mkString(s"$prefix(", " OR ", ")")
                }
                if (values.isEmpty)
                    ""
                else if (values.size <= Solr.Client.maxBooleanClauses)
                    valsToOr(values)
                else
                    ((values grouped Solr.Client.maxBooleanClauses) map valsToOr).mkString("(", " OR ", ")")
        }

        def simplify: QueryPart = this match {
            case RawQuery(rq) if rq.isEmpty ⇒ Empty
            case OrQuery(parts) ⇒
                val newParts = parts.map(_.simplify).filterNot(_ eq Empty)
                if (newParts.nonEmpty) OrQuery(newParts) else Empty
            case AndQuery(parts) ⇒
                val newParts = parts.map(_.simplify).filterNot(_ eq Empty)
                if (newParts.nonEmpty) AndQuery(newParts) else Empty
            case IsAnyOf(_, values) if values.isEmpty ⇒ Empty
            case _ ⇒ this
        }
    }

    case class FieldBuilder(field: Option[String]) {
        def :=(v: FieldValueType) = FieldValue(field, v)
        def :!=(v: FieldValueType) = Not(FieldValue(field, v))
        def isAnyOf(vs: Iterable[FieldValueType]) = if (vs.nonEmpty) IsAnyOf(field, vs) else Empty
        def isNoneOf(vs: Iterable[FieldValueType]) = if (vs.nonEmpty) Not(isAnyOf(vs)) else Empty
        def isInRange(lower: FieldValueType, upper: FieldValueType) = Range(field, lower, upper)
        def exists() = isInRange("*", "*")
        def doesNotExist() = Not(exists())
    }

    trait BuilderMethods {
        def field(f: String) = FieldBuilder(Some(f))
        def defaultField() = FieldBuilder(None)
        def rawQuery(f: String) = RawQuery(f)

        def AND(qps: QueryPart*) = AndQuery(qps)
        def OR(qps: QueryPart*) = OrQuery(qps)
        def NOT(qp: QueryPart) = Not(qp)

        import scala.language.implicitConversions
        implicit def queryPartToQueryBuilder(qp: QueryPart)(implicit arf: ActorRefFactory): SolrQueryBuilder = qp.queryOptions()
    }

    object Methods extends BuilderMethods

    case class OrQuery(parts: Seq[QueryPart]) extends QueryPart

    case class AndQuery(parts: Seq[QueryPart]) extends QueryPart

    case class IsAnyOf(field: Option[String], values: Iterable[FieldValueType]) extends QueryPart

    case class RawQuery(qStr: String) extends QueryPart

    case class FieldValue(field: Option[String], value: FieldValueType) extends QueryPart

    case class Range(field: Option[String], lower: FieldValueType, upper: FieldValueType) extends QueryPart

    case class Not(qp: QueryPart) extends QueryPart

    case object Empty extends QueryPart

    private def valueEsc(value: FieldValueType): String = value match {
        case s: String ⇒
            if (s.contains(" "))
                '"' + s + '"'
            else s

        case _ ⇒ value.toString
    }

    import scala.language.implicitConversions
    implicit def queryOpt2query[T <: QueryPart](qo: Option[T]): QueryPart = qo getOrElse Empty
}
