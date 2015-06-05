package scala.migrations

import java.io.File
import java.nio.file.Files
import java.io.BufferedWriter
import java.io.FileWriter

trait MigrationFilesHandler[T] {
  private lazy val config = MigrationsConfig.config
  protected lazy val unhandledLoc =
    config.getString("migrations.unhandled_location")
  protected lazy val handledLoc =
    config.getString("migrations.handled_location")

  def nameIsId(name: String): Boolean
  def idShouldBeHandled(id: String, appliedIds: Seq[T]): Boolean

  protected def writeSummary(ids: Seq[String]) {
    val code = "object MigrationSummary {\n" + ids.map(
      n => "M" + n).mkString("\n") + "\n}\n"
    val sumFile = new File(handledLoc + "/Summary.scala")

    if (!sumFile.exists) sumFile.createNewFile()
    val fw = new FileWriter(sumFile.getAbsoluteFile())
    val bw = new BufferedWriter(fw)
    bw.write(code)
    bw.close()
  }

  def handleMigrationFiles(appliedMigrationIds: Seq[T]) {
    val unhandled = new File(unhandledLoc)
    assert(unhandled.isDirectory)
    val toMove: Seq[File] = for {
      file <- unhandled.listFiles
      fullName = file.getName
      if fullName.endsWith(".scala")
      name = fullName.substring(0, fullName.length - 6)
      if nameIsId(name)
      if idShouldBeHandled(name, appliedMigrationIds)
    } yield file

    for (file <- toMove) {
      val link = new File(handledLoc + "/" + file.getName)
      val target = new File(
        unhandledLoc + "/" + file.getName).getAbsoluteFile.toPath
      if (!link.exists) {
        println("create link to " + link.toPath + " for " + target)
        Files.createSymbolicLink(link.toPath, target)
      }
    }
    val ids = toMove.map(n => n.getName.substring(0, n.getName.length - 6))
    writeSummary(ids)
  }
  def resetMigrationFiles {
    writeSummary(List())
    val handled = new File(handledLoc)
    val migs = for {
      file <- handled.listFiles
      fullName = file.getName
      if fullName.endsWith(".scala")
      name = fullName.substring(0, fullName.length - 6)
      if nameIsId(name)
    } yield file
    for (file <- migs) {
      file.delete
    }
  }
}

trait RescueCommands[T] {
  this: MigrationFilesHandler[T] =>
  def rescueCommand {
    resetMigrationFiles
  }
}

trait MigrationCommands[T] {
  this: MigrationManager[T] with MigrationFilesHandler[T] =>
  def appliedMigrationIds = alreadyAppliedIds


  def statusCommand: Unit
  def previewCommand: Unit
  def applyCommand: Unit

  def initCommand {
    writeSummary(List())
  }
  def resetCommand {
    resetMigrationFiles
  }
  def updateCommand = {
    handleMigrationFiles(appliedMigrationIds)
  }
}

trait RescueCommandLineTool[T] { this: RescueCommands[T] =>
  def execCommands(args: List[String]) = args match {
    case "rescue" :: Nil => rescueCommand
    case _ => println("Unknown commands")
  }
}

// TODO: This trait may need to be rewritten in the future.
// e.g.: use macros etc. to make adding command (and help info) easier
trait MigrationCommandLineTool[T] { this: MigrationCommands[T] =>

  def execCommands(args: List[String]) = args match {
    case "status" :: Nil => statusCommand
    case "preview" :: Nil => previewCommand
    case "apply" :: Nil => applyCommand
    case "init" :: Nil => initCommand
    case "reset" :: Nil => resetCommand
    case "update" :: Nil => updateCommand
    case _ => println(helpOutput)
  }

  def helpOutput = List("-" * 80,
    "A list of command available in this proof of concept:",
    help, "-" * 80).mkString("\n")

  // TODO: This may need to be rewritten in the future.
  def help = """
  init      create the __migrations__ table which stores version information

  reset     totally clears the database and deletes auto-generated source files
            (this can be used to restart the demo and start again with init)

  status    display the migrations that have not been applied yet

  preview      display the migrations that have not been applied yet and show
            corresponding sql for sql migrations

  apply     apply all migrations which have not been applied yet
            (this could be extended to allow applying only migrations up to a
             stated migration but not further)
"""
}
