package edu.nyu.dlts.aspace

import edu.nyu.dlts.aspace.AspaceClient.AspaceSupport
import org.json4s.JsonAST.{ JValue, JNothing }
import org.json4s.native.JsonMethods._
import java.net.URI

case class TopContainer(uri: URI, indicator: String, barcode: Option[Long])

object Main extends App with AspaceSupport {

  val repositoryId = 7
  val resourceId = 2698

  val resource = getResource(repositoryId, resourceId)
  println((resource.get.json \ "title").extract[String])
  val topContainers = getTopContainers(repositoryId, resourceId)

  println(topContainers.get.json)



  def getTopContainerMap(repositoryId: Int, resourceId: Int): Map[String, TopContainer] = {
    var topContainers = Map[String, TopContainer]()
    var duplicates = List.empty[JValue]
    token match {
      case Some(t) => {
        getTopContainers(repositoryId, resourceId) match {
          case Some(response) => {
            response.json.extract[List[Map[String, String]]].foreach { topContainer =>
              val uri = topContainer("ref")
              getTopContainer(uri) match {
                case Some(tc) => {
                  val json = tc.json
                  val bc = json \ "barcode"
                  val barcode: Option[Long] = if (bc == JNothing) None else Some(bc.extract[String].toLong)
                  val container = new TopContainer(new URI(uri), (json \ "indicator").extract[String], barcode)
                  if (!topContainers.contains(uri)) {
                    topContainers = topContainers + (uri -> container)
                  } else { println("DUPLICATE") }
                }
                case None =>
              }
            }
          }

          case None => throw new Exception("No response from Archivesspace")
        }
      }
      case None => throw new Exception("No token")
    }

    topContainers
  }
}
