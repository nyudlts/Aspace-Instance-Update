package edu.nyu.dlts.aspace

import java.net.{URI, URL}

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpDelete, HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, JValue, JNothing}

import scala.io.Source

case class AspaceResponse(statusCode: Int, json: JValue)
case class TopContainer(uri: String, indicator: String, barcode: Option[Long])

object AspaceClient {

  trait AspaceSupport {

    implicit val formats: DefaultFormats = DefaultFormats


    val conf: Config = ConfigFactory.load()
    val header = "X-ArchivesSpace-Session"
    val client: CloseableHttpClient = getClient(conf.getInt("client.timeout"))
    val env = new URI(conf.getString("env.dev"))
    val token: Option[String] = getToken(conf.getString("test.username"), conf.getString("test.password"), env)

    private def getClient(timeout: Int): CloseableHttpClient = {
      val config = RequestConfig.custom()
        .setConnectTimeout(timeout * 1000)
        .setConnectionRequestTimeout(timeout * 1000)
        .setSocketTimeout(timeout * 1000).build()
      HttpClientBuilder.create().setDefaultRequestConfig(config).build()
    }

    private def getServer(uri: URI): Option[JValue] = {
      try {
        val httpGet = new HttpGet(uri)
        val response = client.execute(httpGet)
        val entity = response.getEntity
        val content = entity.getContent
        val data = scala.io.Source.fromInputStream(content).mkString
        EntityUtils.consume(entity)
        response.close()
        Some(parse(data))
      } catch {
        case e: Exception => None
      }
    }

    private def getToken(user: String, password: String, uri: URI): Option[String] = {
      try {
        val tokenRequest = new HttpPost(uri + s"/users/$user/login?password=$password")
        val response = client.execute(tokenRequest)
        response.getStatusLine.getStatusCode match {
          case 200 => {
            val entity = response.getEntity
            val content = entity.getContent
            val data = Source.fromInputStream(content).mkString
            val json = parse(data)
            val token = (json \ "session").extract[String]
            EntityUtils.consume(entity)
            response.close()
            Some(token)
          }
          case _ => None
        }
      } catch {
        case e: Exception => None
      }
    }

    private def get(httpGet: HttpGet): Option[AspaceResponse] = {
      try {
        val response = client.execute(httpGet)
        val entity = response.getEntity
        val content = entity.getContent
        val data = scala.io.Source.fromInputStream(content).mkString
        val statusCode = response.getStatusLine.getStatusCode
        EntityUtils.consume(entity)
        response.close()
        Some(new AspaceResponse(statusCode, parse(data)))
      } catch {
        case e: Exception => None
      }
    }

    private def getRepository(repositoryId: Int): Option[AspaceResponse] = {
      try {
        val httpGet = new HttpGet(env + s"/repositories/$repositoryId")
        httpGet.addHeader(header, token.get)
        get(httpGet)
      } catch {
        case e: Exception => None
      }
    }

    private def getRepositories: Option[AspaceResponse] = {
      try {
        val httpGet = new HttpGet(env + s"/repositories")
        httpGet.addHeader(header, token.get)
        get(httpGet)
      } catch {
        case e: Exception => None
      }
    }

    private def getTopContainers(repositoryId: Int, resourceId: Int): Option[AspaceResponse] = {
      try {
        val httpGet = new HttpGet(env + s"/repositories/$repositoryId/resources/$resourceId/top_containers")
        httpGet.addHeader(header, token.get)
        get(httpGet)
      } catch {
        case e: Exception => {
          println(e)
          None
        }
      }
    }

    private def getTopContainer(uri: String): Option[AspaceResponse] = {
      try {
        val httpGet = new HttpGet(env + uri)
        httpGet.addHeader(header, token.get)
        get(httpGet)
      } catch {
        case e: Exception => {
          println(e)
          None
        }
      }
    }



    def postAO(aoURI: String, data: String): Option[AspaceResponse] = {
      try {
        val httpPost = new HttpPost(env + aoURI)
        val postEntity = new StringEntity(data, "UTF-8")
        httpPost.addHeader(header, token.get)
        httpPost.setEntity(postEntity)
        httpPost.setHeader("Content-type", "application/json; charset=UTF-8")
        val response = client.execute(httpPost)
        val code = response.getStatusLine
        val responseEntity = response.getEntity
        val content = parse(scala.io.Source.fromInputStream(responseEntity.getContent).mkString)
        val statusLine = response.getStatusLine.getStatusCode.toInt
        EntityUtils.consume(responseEntity)
        EntityUtils.consume(postEntity)
        response.close()
        Some(AspaceResponse(statusLine, content))
      } catch {
        case e: Exception => None
      }
    }

    private def postDO(uri: URI, token: String, repId: Int, data: String): Option[AspaceResponse] = {
      try {

        val httpPost = new HttpPost(uri + s"/repositories/$repId/digital_objects")
        httpPost.addHeader(header, token)
        val postEntity = new StringEntity(data, "UTF-8")
        httpPost.setEntity(postEntity)
        httpPost.setHeader("Content-type", "application/json; charset=UTF-8")
        val response = client.execute(httpPost)
        val responseEntity = response.getEntity
        val content = parse(scala.io.Source.fromInputStream(responseEntity.getContent).mkString)
        val statusLine = response.getStatusLine.getStatusCode.toInt
        EntityUtils.consume(responseEntity)
        EntityUtils.consume(postEntity)
        response.close()
        Some(AspaceResponse(statusLine, content))
      } catch {
        case e: Exception => None
      }
    }

    private def deleteDO(uri: URI, env: String, token: String, doUri: String): Option[AspaceResponse] = {
      try {

        val httpDelete = new HttpDelete(uri + doUri)
        httpDelete.addHeader(header, token)
        val response = client.execute(httpDelete)
        val responseEntity = response.getEntity
        val content = parse(scala.io.Source.fromInputStream(responseEntity.getContent).mkString)
        val statusLine = response.getStatusLine.getStatusCode.toInt
        EntityUtils.consume(responseEntity)
        response.close()
        Some(AspaceResponse(statusLine, content))
      } catch {
        case e: Exception => None
      }

    }

    //Public Methods

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
                    val indicator = (json \ "indicator").extract[String]
                    val barcode: Option[Long] = if (bc == JNothing) None else Some(bc.extract[String].toLong)
                    val container = new TopContainer(uri, indicator, barcode)
                    if (!topContainers.values.toList.contains(container)) {
                      topContainers = topContainers + (indicator -> container)
                    } else {
                      println(s"Ignoring duplicate top container: $container")
                    }
                  }
                  case None => throw new Exception("No Top Container model response from Archivesspace")
                }
              }
            }
            case None => throw new Exception("No Top Container List response from Archivesspace")
          }
        }
        case None => throw new Exception("No token, check login credentials")
      }

      topContainers
    }

    def getResource(repositoryId: Int, resourceId: Int): Option[AspaceResponse] = {
      try {
        val httpGet = new HttpGet(env + s"/repositories/$repositoryId/resources/$resourceId")
        httpGet.addHeader(header, token.get)
        get(httpGet)
      } catch {
        case e: Exception => None
      }
    }

    def getAO(aspace_url: String): Option[AspaceResponse] = {
      try {
        val httpGet = new HttpGet(env + aspace_url)
        httpGet.addHeader(header, token.get)
        get(httpGet)
      } catch {
        case e: Exception => None
      }
    }

  }
}