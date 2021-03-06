/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.models

import cats.Eq
import cats.instances.boolean._
import cats.instances.string._
import cats.syntax.eq._

case class EligibilityCheckResult(result: Either[IneligibilityReason, EligibilityReason])

object EligibilityCheckResult {

  def apply(ineligibilityReason: IneligibilityReason): EligibilityCheckResult =
    EligibilityCheckResult(Left(ineligibilityReason))

  def apply(eligibilityReason: EligibilityReason): EligibilityCheckResult =
    EligibilityCheckResult(Right(eligibilityReason))

}

sealed trait EligibilityReason {
  val legibleString: String
}

sealed trait IneligibilityReason {
  val legibleString: String
}

object EligibilityReason {

  /** In receipt of UC and income sufficient */
  case object UC extends EligibilityReason {
    val legibleString: String =
      "In receipt of UC and income sufficient"
  }

  /** Entitled to WTC and in receipt of positive WTC/CTC Tax Credit */
  case object WTC extends EligibilityReason {
    val legibleString: String =
      "Entitled to WTC and in receipt of positive WTC/CTC Tax Credit"
  }

  /** Entitled to WTC and in receipt of positive WTC/CTC Tax Credit and in receipt of UC and income sufficient */
  case object WTCWithUC extends EligibilityReason {
    val legibleString: String =
      "Entitled to WTC and in receipt of positive WTC/CTC Tax Credit and in receipt of UC and income sufficient"
  }

  val reasons: Set[EligibilityReason] = Set[EligibilityReason](UC, WTC, WTCWithUC)

  def fromString(s: String): Option[EligibilityReason] = reasons.find(_.legibleString === s)
}

object IneligibilityReason {

  implicit def eqInstance: Eq[IneligibilityReason] = new Eq[IneligibilityReason] {
    override def eqv(x: IneligibilityReason, y: IneligibilityReason): Boolean = (x, y) match {
      case (AccountAlreadyOpened, AccountAlreadyOpened) ⇒ true
      case (NotEntitledToWTC(r1), NotEntitledToWTC(r2)) if r1 === r2 ⇒ true
      case (EntitledToWTCButNoWTC(r1), EntitledToWTCButNoWTC(r2)) if r1 === r2 ⇒ true
      case _ ⇒ false
    }
  }

  /** An HtS account was opened previously (the HtS account may have been closed or inactive) */
  case object AccountAlreadyOpened extends IneligibilityReason {
    val legibleString: String =
      "An HtS account was opened previously (the HtS account may have been closed or inactive)"
  }

  /**
   * Not entitled to WTC and
   * (if receivingUC = true)  in receipt of UC but income is insufficient
   * (if receivingUC = false) not in receipt of UC
   */
  case class NotEntitledToWTC(receivingUC: Boolean) extends IneligibilityReason {
    val legibleString: String = if (receivingUC) {
      "Not entitled to WTC and in receipt of UC but income is insufficient"
    } else {
      "Not entitled to WTC and not in receipt of UC"
    }
  }

  /**
   * Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and
   * (if receivingUC = true)  in receipt of UC but income is insufficient
   * (if receivingUC = false) not in receipt of UC
   */
  case class EntitledToWTCButNoWTC(receivingUC: Boolean) extends IneligibilityReason {
    val legibleString: String = if (receivingUC) {
      "Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and in receipt of UC but income is insufficient"
    } else {
      "Entitled to WTC but not in receipt of positive WTC/CTC Tax Credit (nil TC) and not in receipt of UC"
    }
  }

  val reasons: Set[IneligibilityReason] = Set[IneligibilityReason](
    AccountAlreadyOpened,
    NotEntitledToWTC(true),
    NotEntitledToWTC(false),
    EntitledToWTCButNoWTC(true),
    EntitledToWTCButNoWTC(false)
  )

  def fromString(s: String): Option[IneligibilityReason] = reasons.find(_.legibleString === s)

}

