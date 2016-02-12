
package org.broadinstitute.gatk.queue.engine.shell

import org.broadinstitute.gatk.queue.function.CommandLineFunction
import org.broadinstitute.gatk.queue.engine.{CommandLineJobManager, CommandLineJobRunner, RunnerStatus}
import org.broadinstitute.gatk.queue.util.Logging

import scala.collection.mutable.Queue

class RunnerThread(val index : Integer, val parent : MultithreadShellJobManager) extends Thread with Logging {

  var currentRunner : MultithreadShellJobRunner = _

  override def run {

    while(true) {

      parent.synchronized {

        while(parent.jobQueue.isEmpty && !parent.stop)
          parent.wait

        if(parent.stop)
          return;

        currentRunner = parent.jobQueue.dequeue

      }

      logger.info("Thread " + index + " starting process " + currentRunner.description)
      currentRunner.run

    }

  }

  def tryStop {

    parent.synchronized {

      if(currentRunner != null)
        currentRunner.tryStop

    }

  }

}

class MultithreadShellJobManager extends CommandLineJobManager[MultithreadShellJobRunner] {

  def runnerType = classOf[MultithreadShellJobRunner]

  // Also serves as the job-running synchronisation object
  val jobQueue = new Queue[MultithreadShellJobRunner]
  var stop = false

  val runnerThreads = (1 to Runtime.getRuntime.availableProcessors).map(i => new RunnerThread(i, this))
  runnerThreads.map(x => x.start)

  def create(function: CommandLineFunction) = { 
    new MultithreadShellJobRunner(this, function)
  }

  override def tryStop(runners: Set[MultithreadShellJobRunner]) { runners.foreach(_.tryStop) }

  override def exit {
    this.synchronized {
      stop = true
      this.notifyAll
    }
  }

  // No need to poll. Return all, meaning no jobs have been lost.
  override def updateStatus(runners: Set[MultithreadShellJobRunner]) = runners

}

class MultithreadShellJobRunner(val parent : MultithreadShellJobManager, val function: CommandLineFunction) extends CommandLineJobRunner {

  val child = new ShellJobRunner(function)

  override def init {
    child.init
  }

  def start {
    updateStatus(RunnerStatus.RUNNING)
    parent.synchronized {
      parent.jobQueue += this
      parent.notifyAll
    }
  }

  def run {
    child.start
    updateStatus(child.status)
  }

  def tryStop {
    child.tryStop
  }

  def description : String = function.description

  override def cleanup {
    child.cleanup
  }

}
