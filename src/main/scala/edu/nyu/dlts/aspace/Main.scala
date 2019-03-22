package edu.nyu.dlts.aspace

import edu.nyu.dlts.aspace.AspaceClient.AspaceSupport
import org.json4s.JsonAST.{JArray, JString, JValue}
import org.json4s.native.JsonMethods._

import scala.collection.immutable.ListMap
import scala.io.Source



object Main extends App with AspaceSupport {

  val repositoryId = 7
  val resourceId = 2698
  case class TSVRow(resourceId: String, refId: String, uri: String, indicator1: String, indicator2: String, indicator3: Option[String], title: String, componentId: Option[String], newIndicator1: String, newIndicator2: String)
  val resource = getResource(repositoryId, resourceId)
  val topContainers = getTopContainerMap(repositoryId, resourceId)
  println((resource.get.json \ "title").extract[String])
  val workOrder = parseWorkOrder



  workOrder.foreach { row =>

    val r: TSVRow = row._2
    val uri: String = r.uri
    val title: String = r.title
    println(title)
    getAO(uri) match {
      case Some(ao) => {
        var json: JValue = ao.json
         if(r.indicator1 != r.newIndicator1) {
           println("\tUpdating indicator 1")
           val oldIndicator1URI: String = (json \ "instances" \ "sub_container" \ "top_container" \ "ref")(0).extract[String]
           val newIndicator1URI: String = topContainers(r.newIndicator1).uri
           json = updateIndicator1(json, oldIndicator1URI, newIndicator1URI)
         }

        if(r.indicator2 != r.newIndicator2) {
          println("\tUpdating indicator 2")
          json = updateIndicator2(json, r.indicator2, r.newIndicator2)
        }

        val post = postAO(uri, compact(render(json)))

      }
      case None => println(s"\tNo archival object exists at: $uri for $title")
    }

  }

  def updateIndicator1(ao: JValue, oldTCURI: String, newTCURI: String): JValue ={
    println(s"\t\tIndicator1: updating $oldTCURI to $newTCURI")

    val updatedAo = ao.mapField {
      case ("instances", JArray(head :: tail)) => ("instances", JArray(head.mapField {
        case ("ref", JString(oldTCURI)) => ("ref", JString(newTCURI))
        case otherwise => otherwise
      } :: tail))
      case otherwise => otherwise
    }
    val updatedTCURI = (updatedAo \ "instances" \ "sub_container" \ "top_container" \ "ref")(0).extract[String]
    (updatedTCURI == newTCURI) match {
      case true => println(s"\t\t\tsuccess: indicator updated to $newTCURI")
    }
    updatedAo
  }

  def updateIndicator2(ao: JValue, oldIndicator2: String, newIndicator2: String): JValue ={
    println(s"\t\tIndicator2: updating $oldIndicator2 to $newIndicator2")

    val updatedAo = ao.mapField {
      case ("instances", JArray(head :: tail)) => ("instances", JArray(head.mapField {
        case ("indicator_2", JString(oldIndicator2)) => ("indicator_2", JString(newIndicator2))
        case otherwise => otherwise
      } :: tail))
      case otherwise => otherwise
    }

    val updatedInd2 = (updatedAo \ "instances" \ "sub_container" \ "indicator_2")(0).extract[String]
    (updatedInd2 == newIndicator2) match {
      case true => println(s"\t\t\tsuccess: indicator2 updated to $updatedInd2")
    }
    updatedAo
  }

  def parseWorkOrder: Map[String, TSVRow] = {
    var map = Map[String, TSVRow]()
    Source.fromFile("Ghostbusters.tsv").getLines().drop(1).foreach{ row =>
      val fields = row.split("\t")
      val uri = fields(2)
      val TSVRow = new TSVRow(fields(0), fields(1), uri, fields(3), fields(4), None, fields(6), None, fields(8), fields(9))
      map = map + (uri -> TSVRow)
    }
    ListMap(map.toSeq.sortBy(_._1):_*)
  }

}
