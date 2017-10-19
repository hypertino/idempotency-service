package com.hypertino.services.idempotency

import com.hypertino.binders.value._
import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{BadRequest, Conflict, Created, DynamicBody, ErrorBody, HRL, Headers, InternalServerError, MessagingContext, Method, NoContent, NotFound, Ok, RequestBase, ResponseBase}
import com.hypertino.hyperbus.serialization.SerializationOptions
import com.hypertino.hyperbus.subscribe.Subscribable
import com.hypertino.hyperbus.util.{IdGenerator, SeqGenerator}
import com.hypertino.idempotency.api._
import com.hypertino.idempotency.apiref.hyperstorage.{ContentGet, ContentPut, HyperStorageHeader}
import com.hypertino.service.control.api.Service
import com.roundeights.hasher.Hasher
import monix.eval.Task
import monix.execution.Scheduler
import com.typesafe.scalalogging.StrictLogging
import scaldi.{Injectable, Injector}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class IdempotencyServiceConfiguration(responseTtl: FiniteDuration)

class IdempotencyService(implicit val injector: Injector) extends Service with Injectable with Subscribable with StrictLogging {
  protected implicit val scheduler = inject[Scheduler]
  protected val hyperbus = inject[Hyperbus]
  protected val config = IdempotencyServiceConfiguration(8.hours)
  protected val handlers = hyperbus.subscribe(this, logger)
  implicit val so = SerializationOptions.forceOptionalFields
  import so._
  logger.info(s"${getClass.getName} is STARTED")

  def onIdempotentRequestPut(implicit r: IdempotentRequestPut): Task[ResponseBase] = {
    val uriHash = Hasher(r.uri).md5.hex
    val body = DynamicBody(r.body.toValue)
    hyperbus.ask(ContentPut(
      hyperStorageRequestPath(uriHash, r.key),
      body, headers=Headers(
        HyperStorageHeader.HYPER_STORAGE_TTL → Number(config.responseTtl.toSeconds+10l),
        HyperStorageHeader.IF_NONE_MATCH  → "*"
      )))
  }

  def onIdempotentRequestGet(implicit r: IdempotentRequestGet): Task[Ok[RequestInformation]] = {
    val uriHash = Hasher(r.uri).md5.hex
    hyperbus
      .ask(ContentGet(hyperStorageRequestPath(uriHash, r.key)))
      .map {
        case Ok(body: DynamicBody, _) ⇒
          Ok(body.content.to[RequestInformation])
      }
  }

  def onIdempotentResponsePut(implicit r: IdempotentResponsePut): Task[ResponseBase] = {
    val uriHash = Hasher(r.uri).md5.hex
    val body = DynamicBody(Obj.from(
      "headers" → r.body.headers,
      "body" → r.body.body
    ))
    hyperbus.ask(ContentPut(
      hyperStorageResponsePath(uriHash, r.key),
      body, headers=Headers(
      HyperStorageHeader.HYPER_STORAGE_TTL → config.responseTtl.toSeconds
    )))
  }

  def onIdempotentResponseGet(implicit r: IdempotentResponseGet): Task[Ok[ResponseWrapper]] = {
    val uriHash = Hasher(r.uri).md5.hex
    hyperbus
      .ask(ContentGet(hyperStorageResponsePath(uriHash, r.key)))
      .map { ok ⇒
        Ok(ResponseWrapper(ok.body.content.dynamic.headers, ok.body.content.dynamic.body))
      }
  }

  protected def hyperStorageRequestPath(uriHash: String, key: String): String = s"idempotency-service/requests/$uriHash/$key"
  protected def hyperStorageResponsePath(uriHash: String, key: String): String = s"idempotency-service/responses/$uriHash/$key"

  override def stopService(controlBreak: Boolean, timeout: FiniteDuration): Future[Unit] = Future {
    handlers.foreach(_.cancel())
    logger.info(s"${getClass.getName} is STOPPED")
  }
}
