package controllers

import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ControllerHelper
{
  def noCache(result: Result) =
    result.withHeaders(
      "Cache-Control" -> "no-cache, no-store, must-revalidate",
      "Expires" -> "0")
}

/**
 * No cache action
 */
object NoCacheAction extends ActionBuilder[Request]
{
  def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) =
    block(request).map(ControllerHelper.noCache)
}

/**
 * No cache compose action.
 */
case class NoCache[A](action: Action[A]) extends Action[A]
{
  def apply(request: Request[A]): Future[Result] =
    action(request).map(ControllerHelper.noCache)

  lazy val parser = action.parser
}

case class AuthRequest[A](user: models.User, request: Request[A]) extends WrappedRequest[A](request)

/**
 * Action that requires a user to be logged in.
 *
 * Throws up a 401 if no user is logged in.
 */
object AuthenticatedAction extends AuthenticatedBuilder(
  request =>
    Option(Application.getLocalUser(request)),
  implicit request =>
    JavaContext.withContext {
      Results.Unauthorized(views.html.unauthorized.render(""))
    })

/**
 * Authenticate
 */
case class Authenticated[A](action: Action[A]) extends Action[A]
{
  def apply(request: Request[A]): Future[Result] =
    Option(Application.getLocalUser(request)) map { user =>
      action(AuthRequest(user, request))
    } getOrElse {
      Future.successful(JavaContext.withContext({
        Results.Unauthorized(views.html.unauthorized.render(""))
      })(request))
    }

  lazy val parser = action.parser
}

/**
 * Action that requires a user to be logged in.
 *
 * Redirects to a login page if the user is not logged in.
 */
object TryAuthenticateAction extends AuthenticatedBuilder(
  request =>
    Option(Application.getLocalUser(request)),
  implicit request =>
    Results.Redirect(
      routes.Application.login.absoluteURL(request.secure),
      Map("redirect" -> Seq(request.path + "?" + request.rawQueryString)),
      302))


/**
 * Action that that requires an active user or client acting on behalf
 * of a user.
 */
object AuthorizedAction extends AuthenticatedBuilder(
  Application.getActingUser,
  implicit request => JavaContext.withContext {
    Application.getAnyAccessTokenFromRequest(request) flatMap { token =>
      if (token.isExpired)
          Some(Results.Unauthorized(Json.obj())
            .withHeaders("WWW-Authenticate" -> """Bearer error="invalid_token" error_description="Access token is expired""""))
      else
          None
    } getOrElse {
        Results.Unauthorized(Json.obj())
    }})