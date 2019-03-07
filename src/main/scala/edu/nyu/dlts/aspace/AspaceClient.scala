package edu.nyu.dlts.aspace

import java.net.{URI, URL}

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpDelete, HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.json4s.native.JsonMethods.parse
import org.json4s.{DefaultFormats, JValue}

import scala.io.Source

case class AspaceResponse(statusCode: Int, json: JValue)

object AspaceClient {

  trait AspaceSupport {

    implicit val formats: DefaultFormats = DefaultFormats

    val conf: Config = ConfigFactory.load()
    val header = "X-ArchivesSpace-Session"
    val client: CloseableHttpClient = getClient(conf.getInt("client.timeout"))
    val env = new URI(conf.getString("env.dev"))
    val token: Option[String] = getToken(conf.getString("test.username"), conf.getString("test.password"), env)

    def getClient(timeout: Int): CloseableHttpClient = {
      val config = RequestConfig.custom()
        .setConnectTimeout(timeout * 1000)
        .setConnectionRequestTimeout(timeout * 1000)
        .setSocketTimeout(timeout * 1000).build()
      HttpClientBuilder.create().setDefaultRequestConfig(config).build()
    }

    def getServer(uri: URI): Option[JValue] = {
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

    def getToken(user: String, password: String, uri: URI): Option[String] = {
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

    def getAO(uri: URI, token: String, aspace_url: String): Option[JValue] = {
      try {
        val httpGet = new HttpGet(uri + aspace_url)
        httpGet.addHeader(header, token)
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

    def postAO(uri: URI, token: String, aoURI: String, data: String): Option[AspaceResponse] = {
      try {
        val httpPost = new HttpPost(uri + aoURI)
        val postEntity = new StringEntity(data, "UTF-8")
        httpPost.addHeader(header, token)
        httpPost.setEntity(postEntity)
        httpPost.setHeader("Content-type", "application/json; charset=UTF-8")
        val response = client.execute(httpPost)
        val code = response.getStatusLine()
        val responseEntity = response.getEntity
        val content = parse(scala.io.Source.fromInputStream(responseEntity.getContent).mkString)
        val statusLine = response.getStatusLine.getStatusCode.toInt
        EntityUtils.consume(responseEntity)
        EntityUtils.consume(postEntity)
        response.close()
        Some(new AspaceResponse(statusLine, content))
      } catch {
        case e: Exception => None
      }
    }

    def get(httpGet: HttpGet): Option[AspaceResponse] = {
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

    def postDO(uri: URI, token: String, repId: Int, data: String): Option[AspaceResponse] = {
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
        Some(new AspaceResponse(statusLine, content))
      } catch {
        case e: Exception => {
          None
        }
      }
    }

    def deleteDO(uri: URI, env: String, token: String, doUri: String): Option[AspaceResponse] = {
      try {

        val httpDelete = new HttpDelete(uri + doUri)
        httpDelete.addHeader(header, token)
        val response = client.execute(httpDelete)
        val responseEntity = response.getEntity
        val content = parse(scala.io.Source.fromInputStream(responseEntity.getContent).mkString)
        val statusLine = response.getStatusLine.getStatusCode.toInt
        EntityUtils.consume(responseEntity)
        response.close()
        Some(new AspaceResponse(statusLine, content))
      } catch {
        case e: Exception => {
          None
        }
      }
    }

    def getTopContainers(repositoryId: Int, resourceId: Int): Option[AspaceResponse] = {
      try {
        val httpGet = new HttpGet(env + s"/repositories/$repositoryId/resources/$repositoryId/top_containers")
        httpGet.addHeader(header, token.get)
        get(httpGet)
      } catch {
        case e: Exception => {
          println(e)
          None
        }
      }
    }

    def getTopContainer(uri: String): Option[AspaceResponse] = {
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

  }

}