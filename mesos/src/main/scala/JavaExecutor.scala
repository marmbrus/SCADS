package edu.berkeley.cs.scads.mesos

import edu.berkeley.cs.scads.comm._

import org.apache.log4j.Logger
import java.io.File
import mesos._

object JavaExecutor {
  def main(args: Array[String]): Unit = {
    System.loadLibrary("mesos")
    org.apache.log4j.BasicConfigurator.configure()
    val driver = new MesosExecutorDriver(new ScadsExecutor())
    driver.run()
  }
}


class JavaExecutor extends Executor {
  val logger = Logger.getLogger("scads.javaexecutor")
  System.loadLibrary("mesos")

  override def launchTask(d: ExecutorDriver, taskDesc: TaskDescription): Unit = {
    logger.debug("Starting storage handler" + taskDesc.getTaskId())
    val tempDir = File.createTempFile("scads", "mesosJavaExecutor")
    tempDir.delete()
    tempDir.mkdir()

    val processDescription = new JvmProcess().parse(taskDesc.getArg())
    val cmdLine = List("/usr/lib/jvm/java-6-sun/bin/java",
                       "-server",
                       "-Xmx", taskDesc.getParams().get("mem").toInt / (1024*1024) + "M",
                       "-cp", processDescription.classpath,
                       processDescription.mainclass) ++ processDescription.args
    logger.info("Execing: " + cmdLine.mkString)
    val proc = Runtime.getRuntime().exec(cmdLine.toArray, Array[String](), tempDir)
    proc.waitFor()
    d.sendStatusUpdate(new TaskStatus(taskDesc.getTaskId, TaskState.TASK_FINISHED, "".getBytes))
  }
}
