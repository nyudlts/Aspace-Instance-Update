package edu.nyu.dlts.aspace

import org.rogach.scallop.exceptions.{Help, RequiredOptionNotFound, ScallopException}
import org.rogach.scallop.{ScallopConf, ScallopOption}

object CLI {

  trait CLISupport {

    private def help(optionName: String) {
      println(s"Error: Missing required option $optionName")
      help()
    }

    private def error(message: String) {
      println(message)
      println(help())
    }

    private def help(): Unit = {
      println("usage: java -jar DOCreator.jar [options]")
      println("  options:")
      println("    -s, --source, required\t/path/to/workorder.tsv")
      println("    -e, --env, required\t\taspace environment to be used: dev/stage/prod")
      println("    -u, --undo, optional\tundo a previous run")
      println("    -t, --test, optional\ttest mode does not execute any POSTs")
      println("    -h, --help\tprint this message")
      System.exit(0)
    }

    class CLIConf(arguments: Seq[String]) extends ScallopConf(arguments) {
      val source: ScallopOption[String] = opt[String](required = true)
      val env: ScallopOption[String] = opt[String](required = true)
      val undo: ScallopOption[Boolean] = opt[Boolean](required = false)
      val test: ScallopOption[Boolean] = opt[Boolean](required = false)
      verify()
    }

    def getCLI(args: Array[String]): CLIConf = {
      new CLIConf(args) {
        override def onError(e: Throwable): Unit = e match {
          case Help("") => help()
          case ScallopException(message) => error(message)
          case RequiredOptionNotFound(optionName) => help(optionName)
        }
      }
    }

  }
}
