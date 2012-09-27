package com.nominum.build

import java.io.File
import collection.mutable.ListBuffer

package object Util {
  def filesInDirEndingWith(dir: File, end: String): Seq[File] = {
    val files = dir.listFiles
    if (files == null) throw new RuntimeException("Directory " + dir.getAbsolutePath + " not found")

    val filesInDir = files.filter(_.isFile).filter(_.getName.endsWith(end))

    // recurse into subdirectories
    var nestedFiles : ListBuffer[File] = new ListBuffer[File]
    files.filter(_.isDirectory).foreach { dir => nestedFiles ++= filesInDirEndingWith(dir, end) }

    filesInDir ++ nestedFiles
  }

  def filesInDirStartingWith(dir: File, start: String): Seq[File] = {
    val files = dir.listFiles
    if (files == null) throw new RuntimeException("Directory " + dir.getAbsolutePath + " not found")

    val filesInDir = files.filter(_.isFile).filter(_.getName.startsWith(start))

    // recurse into subdirectories
    var nestedFiles : ListBuffer[File] = new ListBuffer[File]
    files.filter(_.isDirectory).foreach { dir => nestedFiles ++= filesInDirStartingWith(dir, start) }

    filesInDir ++ nestedFiles
  }
}
