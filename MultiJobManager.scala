
package org.broadinstitute.gatk.queue.engine.shell

import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.broadinstitute.gatk.queue.engine.{CommandLineJobManager, CommandLineJobRunner, RunnerStatus}
import org.broadinstitute.gatk.queue.util.Logging
import org.broadinstitute.gatk.queue.engine.pbsengine.{PbsEngineJobManager, PbsEngineJobRunner}

class MultiJobManager extends CommandLineJobManager[MultiJobRunner] {

  val pbsManager = new PbsEngineJobManager
  val shellManager = new MultithreadShellJobManager

  def runnerType = classOf[MultiJobRunner]

  override def init {
    shellManager.init
    pbsManager.init
  }

  override def exit {
    shellManager.exit
    pbsManager.exit
  }

  override def tryStop(runners: Set[MultiJobRunner]) {
    val shellJobs = runners.filter(_.isShell)
    val pbsJobs = runners.filter(!_.isShell)
    shellManager.tryStop(shellJobs.map(_.realRunner.asInstanceOf[MultithreadShellJobRunner]))
    pbsManager.tryStop(pbsJobs.map(_.realRunner.asInstanceOf[PbsEngineJobRunner]))
  }

  override def updateStatus(runners: Set[MultiJobRunner]) = {
    val shellJobs = runners.filter(_.isShell)
    val pbsJobs = runners.filter(!_.isShell)

    val goodShellJobs = shellManager.updateStatus(shellJobs.map(_.realRunner.asInstanceOf[MultithreadShellJobRunner]))
    val goodPbsJobs = pbsManager.updateStatus(pbsJobs.map(_.realRunner.asInstanceOf[PbsEngineJobRunner]))

    runners.foreach(_.copyChildStatus)

    val shellJobWrappers = shellJobs.filter(goodShellJobs contains _.realRunner.asInstanceOf[MultithreadShellJobRunner])
    val pbsJobWrappers = pbsJobs.filter(goodPbsJobs contains _.realRunner.asInstanceOf[PbsEngineJobRunner])
    shellJobWrappers ++ pbsJobWrappers
  }

  def create(function: CommandLineFunction) : MultiJobRunner = { 

    val isShell = (!function.jobNativeArgs.isEmpty) && function.jobNativeArgs(0) == "shell"
    function.jobNativeArgs = List()

    val realRunner =
      if(isShell)
        shellManager.create(function)
      else
        pbsManager.create(function)

    new MultiJobRunner(realRunner, isShell)

  }

}

class MultiJobRunner(val realRunner : CommandLineJobRunner, val isShell : Boolean) extends CommandLineJobRunner {

  val function = realRunner.function

  override def init {
    realRunner.init
  }

  def start { 
    realRunner.start
    updateStatus(realRunner.status)
  }

  def copyChildStatus {
    updateStatus(realRunner.status)
  }

  override def cleanup {
    realRunner.cleanup
  }

}
