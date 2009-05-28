/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.HashMap

import org.eclipse.core.resources.{ IFile, IProject, IResourceChangeEvent, IResourceChangeListener, ResourcesPlugin }
import org.eclipse.core.runtime.{ CoreException, FileLocator, IStatus, Platform, Status }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.graphics.Color
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.ScalaCompilationUnitDocumentProvider 

import scala.tools.eclipse.util.Style 

object ScalaPlugin { 
  var plugin : ScalaPlugin = _
}

class ScalaPlugin extends AbstractUIPlugin with IResourceChangeListener {
  ScalaPlugin.plugin = this
  
  val OverrideIndicator = "scala.overrideIndicator"  
  def pluginId = "ch.epfl.lamp.sdt.core"
  def wizardPath = pluginId + ".wizards"
  def wizardId(name : String) = wizardPath + ".new" + name
  def classWizId = wizardId("Class")
  def traitWizId = wizardId("Trait")
  def objectWizId = wizardId("Object")
  def applicationWizId = wizardId("Application")
  def projectWizId = wizardId("Project")
  def netProjectWizId = wizardId("NetProject")
  def editorId : String = "scala.tools.eclipse.Editor"
  def builderId = pluginId + ".scalabuilder"
  def natureId = pluginId + ".scalanature"  
  def launchId = "ch.epfl.lamp.sdt.launching"
  val scalaLib = "SCALA_CONTAINER"
  val scalaHome = "SCALA_HOME"
  def scalaLibId  = launchId + "." + scalaLib
  def scalaHomeId = launchId + "." + scalaHome
  def launchTypeId = "scala.application"
  def problemMarkerId = pluginId + ".marker"
  val scalaFileExtn = ".scala"
  val javaFileExtn = ".java"
  val jarFileExtn = ".jar"
  val ERROR_TYPE = "lampion.error"
  val noColor : Color = null
  val scalaCompilationUnitDocumentProvider = new ScalaCompilationUnitDocumentProvider
  
  private val projects = new HashMap[IProject, ScalaProject]
  
  override def start(context : BundleContext) = {
    super.start(context)
    
    ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE)
    ScalaIndexManager.initIndex(ResourcesPlugin.getWorkspace)
    Platform.getContentTypeManager.
      getContentType(JavaCore.JAVA_SOURCE_CONTENT_TYPE).
        addFileSpec("scala", IContentTypeSettings.FILE_EXTENSION_SPEC)
    Util.resetJavaLikeExtensions // TODO Is this still needed?
    PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
  }

  override def stop(context : BundleContext) = {
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)

    super.stop(context)
  }
  
  def workspaceRoot = ResourcesPlugin.getWorkspace.getRoot
    
  def getJavaProject(project : IProject) = JavaCore.create(project) 

  def getScalaProject(project : IProject) = projects.synchronized {
    projects.get(project) match {
      case Some(scalaProject) => scalaProject
      case None =>
        val scalaProject = new ScalaProject(project)
        projects(project) = scalaProject
        scalaProject
    }
  }
  
  override def resourceChanged(event : IResourceChangeEvent) {
    (event.getResource, event.getType) match {
      case (project : IProject, IResourceChangeEvent.PRE_CLOSE) => 
        projects.synchronized{ projects.remove(project) }
      case _ =>
    }
  }

  def logError(t : Throwable) : Unit = logError("", t)
  
  def logError(msg : String, t : Throwable) : Unit = {
    val t1 = if (t != null) t else { val ex = new Exception ; ex.fillInStackTrace ; ex } 
    val status = new Status(IStatus.ERROR, pluginId, IStatus.ERROR, msg, t1)
    getLog.log(status)
  }
  
  def bundlePath = check {
    val bundle = getBundle 
    val bpath = bundle.getEntry("/")
    val rpath = FileLocator.resolve(bpath)
    rpath.getPath
  }.getOrElse("unresolved")

  final def check[T](f : => T) =
    try {
      Some(f)
    } catch {
      case e : Throwable =>
        logError(e)
        None
    }

  override def initializeDefaultPreferences(store : IPreferenceStore) = {
    super.initializeDefaultPreferences(store)
    Style.initializeEditorPreferences
  }
  
  def isBuildable(file : IFile) = (file.getName.endsWith(scalaFileExtn) || file.getName.endsWith(javaFileExtn))

  def isScalaProject(project : IProject) =
    try {
      project != null && project.isOpen && project.hasNature(natureId)
    } catch {
      case _ : CoreException => false
    }
}
