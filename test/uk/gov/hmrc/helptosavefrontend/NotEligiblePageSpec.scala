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

package uk.gov.hmrc.helptosavefrontend

import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers.{charset, contentType, _}
import uk.gov.hmrc.helptosavefrontend.controllers.HelpToSave
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class NotEligiblePageSpec extends UnitSpec with WithFakeApplication {

  val fakeRequest = FakeRequest("GET", "/")


  "GET /" should {
    "return 200" in {
      val result = HelpToSave.notelgibible(fakeRequest)
      status(result) shouldBe Status.OK
    }

    "return HTML" in {
      val result = HelpToSave.notelgibible(fakeRequest)
      contentType(result) shouldBe Some("text/html")
      charset(result) shouldBe Some("utf-8")
    }
  }

}