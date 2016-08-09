// Copyright 2016, Martin Pokorny <martin@truffulatree.org>
//
// This Source Code Form is subject to the terms of the Mozilla Public License,
// v. 2.0. If a copy of the MPL was not distributed with this file, You can
// obtain one at http://mozilla.org/MPL/2.0/.
//
package org.truffulatree.h2odb.xls

import cats.data.{OneAnd, StateT, Validated, ValidatedNel}
import cats.std.option._
import org.scalatest.{Inside, OptionValues}
import org.truffulatree.h2odb.UnitSpec

class TableSpec extends UnitSpec with Inside with OptionValues {
  implicit val rowSeqSource = RowSeq.source

  type State = Table.State[RowSeq.State]

  type Row = ValidatedNel[Table.Error, Map[String, CellValue]]

  type Source = StateT[Option, State, (Int, Row)]

  def initial(rows: IndexedSeq[Seq[CellValue]]): State =
    new Table.StateUninitialized(RowSeq.initial(rows))

  def toTable(rows: IndexedSeq[Seq[CellValue]]): IndexedSeq[Row] = {
    def step(src: Source, st: State, acc: IndexedSeq[Row]): IndexedSeq[Row] = {
      src.run(st) map { case (st1@_, (_, row@_)) =>
        step(src, st1, acc :+ row)
      } getOrElse acc
    }

    step(Table.source, initial(rows), IndexedSeq.empty)
  }

  "A Table" should "require string values in header row columns" in {
    val rows =
      IndexedSeq(
        List(
          CellString("first"),
          CellNumeric(12.0),
          CellString("third"),
          CellBoolean(false)))

    val table = Table.source.runA(initial(rows))

    table shouldBe defined

    table.value should matchPattern { case (0, Validated.Invalid(_)) => }
  }

  it should "identify columns of non-string values in header row" in {
    val rows =
      IndexedSeq(
        List(
          CellString("first"),
          CellNumeric(12.0),
          CellString("third"),
          CellBoolean(false),
          CellBlank))

    val table = Table.source.runA(initial(rows))

    inside(table.value) {
      case (_, Validated.Invalid(OneAnd(Table.InvalidHeader(cols@_), Nil))) =>
        cols should contain theSameElementsAs Seq(1, 3, 4)
    }
  }

  it should "complete after an erroneous header" in {
    val rows =
      IndexedSeq(
        List(
          CellString("first"),
          CellNumeric(12.0),
          CellString("third"),
          CellBoolean(false)),
        List(
          CellBoolean(true),
          CellString("hello"),
          CellNumeric(-2.2),
          CellString("goodbye")))

    val afterHeader =
      Table.source.runS(initial(rows)) flatMap (s => Table.source.run(s))

    afterHeader shouldBe empty
  }

  it should "provide a map with header values as keys" in {
    val rows =
      IndexedSeq(
        List(
          CellString("first"),
          CellString("second"),
          CellString("third"),
          CellString("fourth")),
        List(
          CellBoolean(true),
          CellString("hello"),
          CellNumeric(-2.2),
          CellString("world")),
        List(
          CellBoolean(false),
          CellString("goodbye"),
          CellNumeric(2.2),
          CellString("nobody")))

    val tableRows = toTable(rows)

    forAll (tableRows) { row =>
      inside(row) { case Validated.Valid(map@_) =>
        map.keySet should contain only ("first", "second", "third", "fourth")
      }
    }

  }
}
