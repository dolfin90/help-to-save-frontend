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

package uk.gov.hmrc.helptosavefrontend.health

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import cats.data.EitherT
import com.miguno.akka.testing.VirtualTime
import com.typesafe.config.ConfigFactory
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthTest.NSIConnectionHealthTestRunner
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthTestRunnerSpec.{PagerDutyAlert, TestNSIConnector}
import uk.gov.hmrc.helptosavefrontend.health.NSIConnectionHealthTestRunnerSpec.TestNSIConnector.{GetTestResult, GetTestResultResponse}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.util.Result
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class NSIConnectionHealthTestRunnerSpec extends TestKit(ActorSystem("NSIConnectionHealthTestRunnerSpec"))
  with ImplicitSender
  with TestSupport {

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  val timeBetweenTests: FiniteDuration = 1.minute

  val numberOfTestsBetweenUpdates: Int = 10

  val maximumConsecutiveFailures: Int = 10

  val numberOfTestsBetweenAlerts: Int = 10

  val time: VirtualTime = new VirtualTime

  val nsiConnector: NSIConnector = new TestNSIConnector(self)

  val pagerDutyListener: TestProbe = TestProbe()

  val payload1: NSIUserInfo = NSIConnectionHealthTestRunner.Payload.Payload1.value

  val payload2: NSIUserInfo = NSIConnectionHealthTestRunner.Payload.Payload2.value

  val runner = system.actorOf(NSIConnectionHealthTest.NSIConnectionHealthTestRunner.props(
    ConfigFactory.parseString(
      s"""
         |health.nsi-connection {
         |  poll-period = ${timeBetweenTests.toString}
         |  poll-count-between-updates = $numberOfTestsBetweenUpdates
         |  poll-count-failures-to-alert = $maximumConsecutiveFailures
         |  poll-count-between-pager-duty-alerts = $numberOfTestsBetweenAlerts
         |}
      """.stripMargin
    ),
    nsiConnector,
    time.scheduler,
    mockMetrics,
    () ⇒ pagerDutyListener.ref ! PagerDutyAlert
  ))

  def mockNSIConnectorTest(userInfo: NSIUserInfo)(result: Option[Either[String, Unit]]): Unit = {
    expectMsg(TestNSIConnector.GetTestResult(userInfo))
    lastSender ! TestNSIConnector.GetTestResultResponse(result)
  }

  "The NSIConnectionHealthTestRunner" must {

    "call the NSI test endpoint after the configured amount of time" in {
      // check that nothing happens before the timer is triggered
      time.advance(timeBetweenTests - 1.millisecond)
      expectNoMsg()

      // now move time forward to when the timer is triggered
      time.advance(1.millisecond)
      mockNSIConnectorTest(payload1)(Some(Right(())))
    }

    "switch payloads once the number of consecutive successes has reached " +
      "the configured number" in {
        // we've already had one successful test - bring the number to one before
        // the configured number
        (2 until numberOfTestsBetweenUpdates).foreach{ _ ⇒
          time.advance(timeBetweenTests)
          mockNSIConnectorTest(payload1)(Some(Right(())))
        }

        // there have been `numberofTestBetweenUpdates - 1` tests with the same payload,
        // the next test should now use the other payload
        time.advance(timeBetweenTests)
        mockNSIConnectorTest(payload2)(Some(Right(())))
      }

  }

}

object NSIConnectionHealthTestRunnerSpec {

  case object PagerDutyAlert

  class TestNSIConnector(reportTo: ActorRef) extends NSIConnector {
    implicit val timeout: Timeout = Timeout(3.seconds)

    override def createAccount(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[NSIConnector.SubmissionResult] =
      sys.error("Not used")

    override def updateEmail(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] =
      sys.error("Not used")

    override def test(userInfo: NSIUserInfo)(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[Unit] = {
      val result: Future[Option[Either[String, Unit]]] = (reportTo ? GetTestResult(userInfo)).mapTo[GetTestResultResponse].map(_.result)
      EitherT(result.flatMap{
        _.fold[Future[Either[String, Unit]]](Future.failed(new Exception("")))(Future.successful)
      })
    }
  }

  object TestNSIConnector {
    case class GetTestResult(payload: NSIUserInfo)

    case class GetTestResultResponse(result: Option[Either[String, Unit]])
  }

}
