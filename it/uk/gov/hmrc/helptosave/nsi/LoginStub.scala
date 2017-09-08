package uk.gov.hmrc.helptosave.nsi

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import play.api.http.HeaderNames
import play.api.libs.Crypto
import play.api.libs.ws.WSCookie
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}
import uk.gov.hmrc.play.http.SessionKeys

trait LoginStub extends SessionCookieBaker {

  val SessionId = s"stubbed-${UUID.randomUUID}"

  private def cookieData(additionalData: Map[String, String]): Map[String, String] = {
    Map(
      SessionKeys.sessionId -> SessionId,
      SessionKeys.userId -> "/auth/oid/1234567890",
      SessionKeys.token -> "token",
      SessionKeys.authProvider -> "GGW",
      SessionKeys.lastRequestTimestamp -> new java.util.Date().getTime.toString
    ) ++ additionalData
  }

  def getSessionCookie(additionalData: Map[String, String] = Map()) = {
    cookieValue(cookieData(additionalData))
  }

  def stubSuccessfulLogin(withSignIn: Boolean = false) = {

    if (withSignIn) {
      val continueUrl = "/wibble"
      stubFor(get(urlEqualTo(s"/gg/sign-in?continue=${continueUrl}"))
        .willReturn(aResponse()
          .withStatus(303)
          .withHeader(HeaderNames.SET_COOKIE, getSessionCookie())
          .withHeader(HeaderNames.LOCATION, continueUrl)))
    }

    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(
            s"""
               |{
               |    "uri": "/auth/oid/1234567890",
               |    "loggedInAt": "2017-06-07T14:57:09.522Z",
               |    "previouslyLoggedInAt": "2017-06-07T14:48:24.841Z",
               |    "accounts": {
               |    },
               |    "levelOfAssurance": "2",
               |    "confidenceLevel" : 200,
               |    "credentialStrength": "strong",
               |    "legacyOid":"1234567890"
               |}
               |
            """.stripMargin
          )))
  }
}

trait SessionCookieBaker {
  val cookieKey = "gvBoGdgzqG1AarzF1LY0zQ=="
  def cookieValue(sessionData: Map[String, String]) = {
      def encode(data: Map[String, String]): PlainText = {
        val encoded = data.map {
          case (k, v) ⇒ URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }.mkString("&")
        val key = "yNhI04vHs9<_HWbC`]20u`37=NGLGYY5:0Tg5?y`W<NoJnXWqmjcgZBec@rOxb^G".getBytes
        PlainText(Crypto.sign(encoded, key) + "-" + encoded)
      }

    val encodedCookie = encode(sessionData)
    val encrypted = CompositeSymmetricCrypto.aesGCM(cookieKey, Seq()).encrypt(encodedCookie).value

    s"""mdtp="$encrypted"; Path=/; HTTPOnly"; Path=/; HTTPOnly"""
  }

  def getCookieData(cookie: WSCookie): Map[String, String] = {
    getCookieData(cookie.value.get)
  }

  def getCookieData(cookieData: String): Map[String, String] = {

    val decrypted = CompositeSymmetricCrypto.aesGCM(cookieKey, Seq()).decrypt(Crypted(cookieData)).value
    val result = decrypted.split("&")
      .map(_.split("="))
      .map { case Array(k, v) ⇒ (k, URLDecoder.decode(v, StandardCharsets.UTF_8.name())) }
      .toMap

    result
  }
}
