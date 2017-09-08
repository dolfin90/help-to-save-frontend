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

package hts.utils

import uk.gov.hmrc.domain.Generator

trait NINOGenerator {

  private val generator = new Generator()

  private var current = generator.nextNino.value

  private def generateNINO: String = {
    current = generator.nextNino.value
    current
  }

  private def toEligible(nino: String) = "AE" + nino.drop(2)

  def generateEligibleNINO: String = toEligible(generateNINO)

  def generateIneligibleNINO: String = {
    val ineligibleNino = "NA" + generateNINO.drop(2)
    ineligibleNino
  }

  def currentEligibleNINO: String = toEligible(current)

}

//object NINOGenerator {
//  private val generator = new Generator()
//
//  private var current = generator.nextNino.value
//}
