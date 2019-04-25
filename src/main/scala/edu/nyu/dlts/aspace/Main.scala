package edu.nyu.dlts.aspace

import edu.nyu.dlts.aspace.CLI.CLISupport
import java.io.File
import java.net.URI
import org.json4s.JsonAST.{JArray, JString, JValue}
import org.json4s.{DefaultFormats, JNothing, JValue}
import org.json4s.native.JsonMethods._
import scala.collection.immutable.ListMap
import scala.io.Source

object Main extends App with CLISupport {

  case class TSVRow(resourceId: String, refId: String, uri: URI, indicator1: String, indicator2: String, indicator3: Option[String], title: String, componentId: Option[String], newIndicator1: String, newIndicator2: String)
  case class ResourceRepository(repositoryId: Int, resourceURI: URI, resourceTitle: String)

  //initialize values
  implicit val formats: DefaultFormats = DefaultFormats
  val cliConf: CLIConf = getCLI(args)
  val client = new AspaceClient(cliConf.env.toOption.get)
  val workOrder: File = new File(cliConf.source.toOption.get)
  val tsvRows = parseWorkOrder(workOrder)
  val rr: ResourceRepository = getRepResource(tsvRows.head._2.uri)
  val topContainers = client.getTopContainerMap(rr.resourceURI)
  val undo = cliConf.undo.toOption.get
  val test = cliConf.test.toOption.get

  //start the program
  println("NYU Instance Updater v0.0")
  println(s"  processing ${rr.resourceTitle}")

  //process the Work Order
  tsvRows.foreach { tsvRow => updateAO(tsvRow._2) }

  def parseWorkOrder(wo: File): Map[String, TSVRow] = {
    var map = Map[String, TSVRow]()
    Source.fromFile(wo).getLines().drop(1).foreach{ row =>
      val fields = row.split("\t")
      val uri = fields(2)
      val TSVRow = new TSVRow(fields(0), fields(1), new URI(uri), fields(3), fields(4), None, fields(6), None, fields(8), fields(9))
      map = map + (uri -> TSVRow)
    }
    ListMap(map.toSeq.sortBy(_._1):_*)
  }

  def getRepResource(aoURI: URI): ResourceRepository = {
    val repoId = aoURI.toString.split("/")(2).toInt
    val ao = client.getAO(aoURI).get.body
    val resourceURI = new URI((ao \ "resource" \ "ref").extract[String])
    val resource = client.getResource(resourceURI).get.body
    val resourceTitle = (resource \ "title").extract[String]
    new ResourceRepository(repoId, resourceURI, resourceTitle)
  }

  def updateAO(row: TSVRow): Unit = {

    val uri = row.uri
    val title: String = row.title
    println("\t" + title)

    client.getAO(uri) match {
      case Some (ao) => {
        var json: JValue = ao.body
        val aspaceIndicator1URI = new URI((json \ "instances" \ "sub_container" \ "top_container" \ "ref") (0).extract[String])
        val aspaceIndicator2: String = (json \ "instances" \ "sub_container" \ "indicator_2") (0).extract[String]
        val tsvInd1 = row.indicator1
        val tsvInd2 = row.indicator2
        val tsvNewInd1 = row.newIndicator1
        val tsvNewInd2 = row.newIndicator2

        undo match {
          case false => {
            val newUri = topContainers(tsvNewInd1).uri

            if(aspaceIndicator1URI != newUri) {
              json = updateIndicator1(json,aspaceIndicator1URI, newUri)
            }

            if(aspaceIndicator2 != tsvNewInd2) {
              json = updateIndicator2(json, aspaceIndicator2, tsvNewInd2)
            }
          }
          //logic for undoing an operation
          case true => {
            val newUri = topContainers(tsvInd1).uri
            if(aspaceIndicator1URI != newUri) {
              json = updateIndicator1(json, aspaceIndicator1URI, newUri)
            }

            if(aspaceIndicator2 != tsvInd2) {
              json = updateIndicator2(json, aspaceIndicator2, tsvInd2)
            }
          }
        }

        if(test == false) { client.postAO(uri, compact(render(json))) }

      }
      case None => println (s"\tNo archival object exists at: $uri for $title")
    }

  }

  //json methods
  def updateIndicator1(ao: JValue, oldTCURI: URI, newTCURI: URI): JValue ={

    println("\t  updating top container uri")
    val updatedAo = ao.mapField {
      case ("instances", JArray(head :: tail)) => ("instances", JArray(head.mapField {
        case ("ref", JString(oldTCURI)) => ("ref", JString(newTCURI.toString))
        case otherwise => otherwise
      } :: tail))
      case otherwise => otherwise
    }

    val updatedTCURI = new URI((updatedAo \ "instances" \ "sub_container" \ "top_container" \ "ref")(0).extract[String])
    (updatedTCURI == newTCURI) match {
      case true => println(s"\t\t success: indicator updated to $newTCURI")
      case false => println(s"\t\t failure -- container 2 not updated")
    }
    updatedAo
  }

  def updateIndicator2(ao: JValue, oldIndicator2: String, newIndicator2: String): JValue ={
    println(s"\t  Indicator2: updating $oldIndicator2 to $newIndicator2")

    val updatedAo = ao.mapField {
      case ("instances", JArray(head :: tail)) => ("instances", JArray(head.mapField {
        case ("indicator_2", JString(oldIndicator2)) => ("indicator_2", JString(newIndicator2))
        case otherwise => otherwise
      } :: tail))
      case otherwise => otherwise
    }

    val updatedInd2 = (updatedAo \ "instances" \ "sub_container" \ "indicator_2")(0).extract[String]
    (updatedInd2 == newIndicator2) match {
      case true => println(s"\t\t success: indicator2 updated to $updatedInd2")
      case false => println(s"\t\t failure -- container 2 not updated")
    }
    updatedAo
  }

}
