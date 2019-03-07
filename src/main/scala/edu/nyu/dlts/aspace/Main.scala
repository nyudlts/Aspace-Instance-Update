package edu.nyu.dlts.aspace

import edu.nyu.dlts.aspace.AspaceClient.AspaceSupport
import org.json4s.JsonAST.JNothing
import org.json4s.native.JsonMethods._
import java.net.URI

case class TopContainer(uri: URI, indicator: String, barcode: Option[Long])

object Main extends App with AspaceSupport {
  token match {
    case Some(t) => {
      getTopContainers(2, 742) match {
        case Some(response) => {
          response.json.extract[List[Map[String, String]]].foreach{ topContainer =>
            val uri = (topContainer("ref"))
            getTopContainer(uri) match {
              case Some(tc) => {
                val json = tc.json
                //println(compact(render(json)))
                val bc = (json \ "barcode")
                val barcode: Option[Long] = if(bc == JNothing) None  else Some(bc.extract[String].toLong)
                val container = new TopContainer(new URI(uri),(json \ "indicator").extract[String], barcode)
                println(container)
              }
              case None =>
            }
          }
        }

        case None => println("no response")
      }
    }
    case None => throw new Exception("No token")
  }

}
