package controllers

import java.text.SimpleDateFormat
import java.util.Date
import play.Routes
import play.api.mvc._
import play.core.j.JavaHelpers

import providers.MyUsernamePasswordAuthProvider
import com.feth.play.module.pa.PlayAuthenticate
import com.feth.play.module.pa.user.AuthUser
import scala.concurrent._

/**
 * Main application controller.
 */
object Application extends Controller
{
  /**
   * Get the current logged in user for a session.
   */
  def getLocalUser(user: AuthUser): models.User =
    models.User.findByAuthUserIdentity(user).getOrElse(null)

  def getLocalUser(session: play.mvc.Http.Session): models.User =
    getLocalUser(PlayAuthenticate.getUser(session))

  def getLocalUser(request: Request[_]): models.User =
    getLocalUser(JavaHelpers.createJavaContext(request).session())

  def getLocalUser(request: RequestHeader): models.User =
    getLocalUser(JavaHelpers.createJavaContext(request).session())

  /**
   * Get the user that is being acted on behalf of.
   */
  def getTokenUser(request: RequestHeader): Option[models.User] =
    getAccessTokenFromRequest(request) flatMap { access_token =>
      models.AccessToken.findValidByAccessToken(access_token)
    } flatMap { token =>
      token.getUser
    }

  private def getAccessTokenFromRequest(request: RequestHeader): Option[String] =
    request.getQueryString("access_token") orElse {
      request.headers.get("Authorization") flatMap { authorization =>
        """Bearer (\w+)""".r.findFirstMatchIn(authorization) map { found =>
          found.group(1)
        }
      }
    }

  /**
   * Get the acting user.
   */
  def getActingUser(request: RequestHeader): Option[models.User] =
    getTokenUser(request) orElse (Option(Application.getLocalUser(request)))

  /**
   * Index page.
   *
   * Renders hero page for non logged in users or the users's root stream for logged in users.
   *
   * Redirects to the select user name page if a user has not selected a user name.
   */
  def index = Action.async { implicit request => JavaContext.withContext {
    val localUser = getLocalUser(request)
    if (localUser == null) {
      Future.successful(Ok(views.html.index.render()))
    } else if (localUser.userName == null || localUser.userName.isEmpty()) {
      Future.successful(Redirect(routes.Account.selectUserName()))
    } else {
      controllers.Stream.getStream(localUser.userName).apply(request)
    }
  }}

  /**
   * Login page.
   */
  def login = Action { implicit request => JavaContext.withContext {
    Ok(views.html.login.render(MyUsernamePasswordAuthProvider.LOGIN_FORM))
      .withSession(
        "redirect" -> request.getQueryString("redirect").getOrElse(""))
  }}

  /**
   * Post login handler.
   */
  def onLogin = AuthenticatedAction { implicit request => JavaContext.withContext {
    request.session.get("redirect") flatMap { redirect =>
      if (redirect.startsWith("/"))
          Some(Redirect(redirect))
      else
        None
    } getOrElse(Redirect(routes.Application.index()))
  }}

  /**
   *
   */
  def notFound = Action { implicit request => JavaContext.withContext {
    NotFound(views.html.notFound.render(""))
  }}

  /**
   *
   */
  def unauthorized = Action { implicit request => JavaContext.withContext {
    Unauthorized(views.html.unauthorized.render(""))
  }}

  def formatTimestamp(t: Long): String =
    new SimpleDateFormat("yyyy-dd-MM HH:mm:ss").format(new Date(t))
}
