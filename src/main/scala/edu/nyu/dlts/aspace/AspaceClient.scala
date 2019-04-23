package edu.nyu.dlts.aspace

import com.typesafe.config.{Config, ConfigFactory}
import java.net.URI
import org.apache.http.Header
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.{HttpDelete, HttpGet, HttpPost}
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.apache.http.util.EntityUtils
import org.json4s.native.JsonMethods._
import org.json4s.{DefaultFormats, JNothing, JValue}
import scala.collection.immutable.ListMap

case class AspaceResponse(statusCode: Int, headers: Array[Header], body: JValue)
case class TopContainer(uri: URI, indicator: String, barcode: Option[Long], kind: Option[String])

class AspaceClient(environment: String) {

  implicit val formats: DefaultFormats = DefaultFormats
  protected val conf: Config = ConfigFactory.load()
  protected val header = "X-ArchivesSpace-Session"
  protected val client: CloseableHttpClient = getClient(conf.getInt("client.timeout"))
  protected val env = new URI(conf.getString(s"env.$environment.uri"))
  protected val port = conf.getInt(s"env.$environment.port")
  protected val token: String = getToken(conf.getString(s"env.$environment.login.username"), conf.getString(s"env.$environment.login.password"), env)
  protected val exception = "request caused an exception: "

  //private functions
  private def getClient(timeout: Int): CloseableHttpClient = {
    val config = RequestConfig.custom()
      .setConnectTimeout(timeout)
      .setConnectionRequestTimeout(timeout)
      .setSocketTimeout(timeout).build()
    HttpClientBuilder.create().setDefaultRequestConfig(config).build()
  }

  private def getBuilder: URIBuilder = {
    val builder = new URIBuilder()
    builder.setScheme("https")
    builder.setHost(env.toString)
    builder.setPort(port)
    builder
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

  private def getToken(user: String, password: String, uri: URI): String = {
    try {
      val builder = getBuilder
      builder.setPath(s"/users/$user/login")
      builder.addParameter("password", password)
      val request = builder.build()
      val httpPost = new HttpPost(request)
      val response = post(httpPost).get
      val statusCode = response.statusCode

      statusCode match {
        case 200 => (response.body \ "session").extract[String]
        case _ => throw new Exception(s"Request for token returned an error code: ${statusCode.toString}")
      }
    } catch {
      case e: Exception => throw new Exception("POST " + exception + e)
    }
  }

  private def get(httpGet: HttpGet): Option[AspaceResponse] = {
    try {
      val response = client.execute(httpGet)
      val entity = response.getEntity
      val content = entity.getContent
      val data = scala.io.Source.fromInputStream(content).mkString
      val statusCode = response.getStatusLine.getStatusCode
      val headers = response.getAllHeaders
      EntityUtils.consume(entity)
      response.close()
      Some(new AspaceResponse(statusCode, headers, parse(data)))
    } catch {
      case e: Exception => throw new Exception("GET " + exception + e)
    }
  }

  private def post(httpPost: HttpPost): Option[AspaceResponse] = {
    try {
      val response = client.execute(httpPost)
      val entity = response.getEntity
      val content = entity.getContent
      val data = scala.io.Source.fromInputStream(content).mkString
      val statusCode = response.getStatusLine.getStatusCode
      val headers = response.getAllHeaders
      EntityUtils.consume(entity)
      response.close()
      Some(AspaceResponse(statusCode, headers, parse(data)))
    } catch {
      case e: Exception => throw new Exception("POST " + exception + e)
    }
  }

  private def delete(httpDelete: HttpDelete): Option[AspaceResponse] = {
    try {
      val response = client.execute(httpDelete)
      val entity = response.getEntity
      val content = entity.getContent
      val data = scala.io.Source.fromInputStream(content).mkString
      val statusCode = response.getStatusLine.getStatusCode
      val headers = response.getAllHeaders
      EntityUtils.consume(entity)
      response.close()
      Some(new AspaceResponse(statusCode, headers, parse(data)))
    } catch {
      case e: Exception => throw new Exception("DELETE " + exception + e)
    }
  }

  //Public functions
  def getRepository(repositoryId: Int): Option[AspaceResponse] = {
    val httpGet = new HttpGet(env + s"/repositories/$repositoryId")
    httpGet.addHeader(header, token)
    get(httpGet)
  }

  def getRepositories: Option[AspaceResponse] = {
    val httpGet = new HttpGet(env + s"/repositories")
    httpGet.addHeader(header, token)
    get(httpGet)
  }


  def postAO(aoURI: URI, data: String): Option[AspaceResponse] = {
    val builder = getBuilder
    builder.setPath(aoURI.toString)
    val httpPost = new HttpPost(builder.build)
    val postEntity = new StringEntity(data, "UTF-8")
    httpPost.addHeader(header, token)
    httpPost.setEntity(postEntity)
    httpPost.setHeader("Content-type", "application/json; charset=UTF-8")
    post(httpPost)
  }

  private def postDO(uri: URI, token: String, repId: Int, data: String): Option[AspaceResponse] = {
    val httpPost = new HttpPost(uri + s"/repositories/$repId/digital_objects")
    httpPost.addHeader(header, token)
    val postEntity = new StringEntity(data, "UTF-8")
    httpPost.setEntity(postEntity)
    httpPost.setHeader("Content-type", "application/json; charset=UTF-8")
    post(httpPost)
  }

  private def deleteDO(uri: URI, env: String, token: String, doUri: String): Option[AspaceResponse] = {
    val httpDelete = new HttpDelete(uri + doUri)
    httpDelete.addHeader(header, token)
    delete(httpDelete)
  }

  def getTopContainerMap(resourceURI: URI): Map[String, TopContainer] = {
    var topContainers = Map[String, TopContainer]()
    var duplicates = List.empty[JValue]

    val response = getTopContainers(resourceURI).get

    response.body.extract[List[Map[String, String]]].foreach { tc =>
      val containerURI = new URI(tc("ref"))
      val topContainer = getTopContainer(containerURI)
      val json = topContainer.get.body
      val bc = json \ "barcode"

      val kind = ((json \ "kind") == JNothing) match {
        case true => None
        case false => Some((json \ "kind").extract[String])
      }

      val indicator = (json \ "indicator").extract[String]
      val barcode: Option[Long] = if (bc == JNothing) None else Some(bc.extract[String].toLong)
      val container = new TopContainer(containerURI, indicator, barcode, kind)
      if (!topContainers.values.toList.contains(container)) {
        topContainers = topContainers + (indicator -> container)
      } else {
        println(s"Ignoring duplicate top container: $container")
      }
    }
    ListMap(topContainers.toSeq.sortBy(_._1):_*)
  }

  def getTopContainers(resourceURI: URI): Option[AspaceResponse] = {
    val httpGet = new HttpGet(getBuilder.setPath(resourceURI + "/top_containers").build())
    httpGet.addHeader(header, token)
    get(httpGet)
  }

  def getTopContainer(containerURI: URI): Option[AspaceResponse] = {
    val httpGet = new HttpGet(getBuilder.setPath(containerURI.toString).build())
    httpGet.addHeader(header, token)
    get(httpGet)
  }

  def getResource(uri: URI): Option[AspaceResponse] = {
    val builder = new URIBuilder()
    builder.setScheme("https")
    builder.setHost(env.toString)
    builder.setPort(port)
    builder.setPath(uri.toString)
    val httpGet = new HttpGet(builder.build())
    httpGet.addHeader(header, token)
    get(httpGet)
  }

  def getAO(aspace_url: URI): Option[AspaceResponse] = {
    val builder = getBuilder
    builder.setPath(aspace_url.toString)
    val httpGet = new HttpGet(builder.build())
    httpGet.addHeader(header, token)
    get(httpGet)
  }

  def search(repositoryId: Int, page: Int, pageSize: Int, aq: String): Option[AspaceResponse] = {
    val builder = new URIBuilder(s"$env/repositories/$repositoryId/search")
    builder.setParameter("page", page.toString)
    builder.setParameter("page_size", pageSize.toString)
    builder.setParameter("aq", aq)
    val httpGet = new HttpGet(builder.build())
    httpGet.addHeader(header, token)
    get(httpGet)
  }

}
