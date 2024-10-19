package sangria.http.pekko

import org.apache.pekko.http.scaladsl.model.MediaType
import org.apache.pekko.http.scaladsl.model.headers.Accept
import org.apache.pekko.http.scaladsl.server.Directive0
import org.apache.pekko.http.scaladsl.server.Directives.{headerValuePF, pass}

object Util {
  def explicitlyAccepts(mediaType: MediaType): Directive0 =
    headerValuePF {
      case Accept(ranges)
          if ranges.exists(range => !range.isWildcard && range.matches(mediaType)) =>
        ranges
    }.flatMap(_ => pass)
}
