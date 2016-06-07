// Copyright 2016, Martin Pokorny <martin@truffulatree.org>
//
// This Source Code Form is subject to the terms of the Mozilla Public License,
// v. 2.0. If a copy of the MPL was not distributed with this file, You can
// obtain one at http://mozilla.org/MPL/2.0/.
//
package org.truffulatree.h2odb

import java.util.Date

import cats.Apply
import cats.data._
import cats.std.list._
import cats.syntax.option._
import org.truffulatree.h2odb.xls._

final case class AnalysisRecord(
  parameter: String,
  test: String,
  samplePointId: String,
  reportedND: String,
  lowerLimit: Option[Float],
  dilution: Float,
  method: String,
  total: Option[String],
  units: String,
  sampleNumber: String,
  analysisTime: Date)

object AnalysisRecord {
  def fromXlsRow(row: Map[String, CellValue]):
      ValidatedNel[Error, AnalysisRecord] = {

    def fieldValue[A](name: String)(fn: PartialFunction[CellValue, A]):
        ValidatedNel[Error, Option[A]] = {
      val getValue: PartialFunction[CellValue, Xor[Error, Option[A]]] =
        (fn andThen (a => Xor.right(Some(a)))).
          orElse {
            case CellBlank => Xor.right(none)
            case _ => Xor.left(FieldType(name))
          }

      Xor.fromOption(row.get(name), MissingField(name)).
        flatMap(getValue).toValidatedNel
    }

    def required[A](
      fv: String => PartialFunction[CellValue, A] => ValidatedNel[Error, Option[A]]):
        String => PartialFunction[CellValue, A] => ValidatedNel[Error, A] = {
      name => fn =>
      fv(name)(fn) andThen (opt =>
        Validated.fromOption(opt, MissingField(name)).toValidatedNel)
    }

    def stringValue(name: String): ValidatedNel[Error, Option[String]] =
      fieldValue(name) { case CellString(s@_) => s }

    def requiredStringValue(name: String): ValidatedNel[Error, String] =
      required(fieldValue[String])(name) { case CellString(s@_) => s }

    /* val requiredStringValue = required(_: String)(stringValue)
     * 
     * def requiredFieldValue[A](fn: PartialFunction[CellValue, A]) =
     *   required(_: String)(fieldValue[A](_)(fn)) */

    val vParameter = requiredStringValue("Param")
    val vTest = requiredStringValue("Test")
    val vSamplePointId = requiredStringValue("SamplePointID")
    val vReportedND = requiredStringValue("ReportedND")
    val vLowerLimit = fieldValue("LowerLimit") {
        case CellNumeric(n@_) => n.toFloat
      }
    val vDilution = required[Float](fieldValue)("Dilution") {
        case CellNumeric(n@_) => n.toFloat
      }
    val vMethod = requiredStringValue("Method")
    val vTotal = stringValue("Total")
    val vUnits = requiredStringValue("Results_Units")
    val vSampleNumber = requiredStringValue("SampleNumber")
    val vAnalysisTime = required[Date](fieldValue)("AnalysisTime") {
        case CellDate(d@_) => d
      }

    Apply[ValidatedNel[Error, ?]].map11(
      vParameter,
      vTest,
      vSamplePointId,
      vReportedND,
      vLowerLimit,
      vDilution,
      vMethod,
      vTotal,
      vUnits,
      vSampleNumber,
      vAnalysisTime) {
      case (param@_, test@_, spid@_, rnd@_, ll@_, dil@_, mth@_, tot@_, un@_, num@_, at@_) =>
        AnalysisRecord(param, test, spid, rnd, ll, dil, mth, tot, un, num, at)
    }
  }

  sealed trait Error
  final case class MissingField(name: String) extends Error
  final case class FieldType(name: String) extends Error
}