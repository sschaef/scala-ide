package org.scalaide.core.internal.statistics

import java.io.File
import java.io.FileWriter

import scala.collection.JavaConverters._

import org.scalaide.core.ScalaIdeDataStore
import org.scalaide.core.internal.ScalaPlugin

import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter

import Features._

class Statistics {

  private var firstStat = 0L
  private var cache = Map[Feature, FeatureData]()
  private val jsonArgs = Map[String, AnyRef](JsonWriter.PRETTY_PRINT → "true").asJava

  readStats()

  def data: Seq[FeatureData] = cache.values.toList
  def startOfStats: Long = firstStat

  def incUses(feature: Feature, numToInc: Int = 1): Unit = {
    val stat = cache.getOrElse(feature, FeatureData(feature, 0, System.currentTimeMillis))
    cache += feature → stat.copy(nrOfUses = stat.nrOfUses + numToInc, lastUsed = System.currentTimeMillis)

    writeStats()
  }

  private def readStats(): Unit = {
    ScalaIdeDataStore.read(ScalaIdeDataStore.statisticsLocation) { file ⇒
      val stats = read(file)
      firstStat = stats.firstStat
      cache = stats.featureData.map(stat ⇒ stat.feature → stat)(collection.breakOut)
    }
  }

  private def writeStats(): Unit = {
    if (firstStat == 0) firstStat = System.currentTimeMillis
    val stats = StatData(firstStat, cache.map(_._2)(collection.breakOut))

    ScalaIdeDataStore.write(ScalaIdeDataStore.statisticsLocation) { file ⇒
      write(file, stats)
    }
  }

  private def write(file: File, value: StatData): Unit = {
    val json = JsonWriter.objectToJson(value, jsonArgs)
    new FileWriter(file).append(json).close()
  }

  private def read(file: File): StatData = {
    val json = io.Source.fromFile(file).mkString
    JsonReader.jsonToJava(json).asInstanceOf[StatData]
  }
}

object Groups {
  abstract class Group(val description: String)
  object Miscellaneous extends Group("Miscellaneous")
  object QuickAssist extends Group("Quick Assist")
  object Refactoring extends Group("Refactoring")
  object Editing extends Group("Editing")
  object SaveAction extends Group("Save Action")
  object AutoEdit extends Group("Auto Edit")
  object Wizard extends Group("Wizard")
}

object Features {
  import Groups._

  case class Feature(id: String)(val description: String, val group: Group) {
    def incUses(numToInc: Int = 1): Unit =
      ScalaPlugin().statistics.incUses(this, numToInc)
  }
  object ExplicitReturnType extends Feature("ExplicitReturnType")("Add explicit return type", QuickAssist)
  object InlineLocalValue extends Feature("InlineLocalValue")("Inline local value", QuickAssist)
  object ExpandCaseClassBinding extends Feature("ExpandCaseClassBinding")("Expand case class binding", QuickAssist)
  object ExpandImplicitConversion extends Feature("ExpandImplicitConversion")("Expand implicit conversion", QuickAssist)
  object ExpandImplicitArgument extends Feature("ExpandImplicitArgument")("Expand implicit argument", QuickAssist)
  object FixTypeMismatch extends Feature("FixTypeMismatch")("Fix type mismatch", QuickAssist)
  object ImportMissingMember extends Feature("ImportMissingMember")("Import missing member", QuickAssist)
  object CreateClass extends Feature("CreateClass")("Create class", QuickAssist)
  object FixSpellingMistake extends Feature("FixSpellingMistake")("Fix spelling mistake", QuickAssist)
  object CreateMethod extends Feature("CreateMethod")("Create method", QuickAssist)
  object ExtractCode extends Feature("ExtractCode")("Extract code", QuickAssist)
  object CopyQualifiedName extends Feature("CopyQualifiedName")("Copy qualified name", Miscellaneous)
  object RestartPresentationCompiler extends Feature("RestartPresentationCompiler")("Restart Presentation Compiler", Miscellaneous)
  /** Exists for backward compatibility with previous versions of the IDE. */
  object NotSpecified extends Feature("NotSpecified")("<not specified>", Miscellaneous)
  object CodeAssist extends Feature("CodeAssist")("Code completion", Editing)
  object CharactersSaved extends Feature("CharactersSaved")("Number of typed characters saved thanks to code completion", Editing)
  object OrganizeImports extends Feature("OrganizeImports")("Organize imports", Refactoring)
  object ExtractMemberToTrait extends Feature("ExtractMemberToTrait")("Extract member to trait", Refactoring)
  object MoveConstructorToCompanion extends Feature("MoveConstructorToCompanion")("Move constructor to companion object", Refactoring)
  object GenerateHashcodeAndEquals extends Feature("GenerateHashcodeAndEquals")("Generate hashCode and equals method", Refactoring)
  object IntroduceProductNTrait extends Feature("IntroduceProductNTrait")("Introduce ProductN trait", Refactoring)
  object LocalRename extends Feature("LocalRename")("Rename local value", Refactoring)
  object GlobalRename extends Feature("GlobalRename")("Rename global value", Refactoring)
  object MoveClass extends Feature("MoveClass")("Move class/object/trait", Refactoring)
  object SplitParameterLists extends Feature("SplitParameterLists")("Split parameter lists", Refactoring)
  object MergeParameterLists extends Feature("MergeParameterLists")("Merge parameter lists", Refactoring)
  object ChangeParameterOrder extends Feature("ChangeParameterOrder")("Change parameter order", Refactoring)
}

final case class StatData(firstStat: Long, featureData: Array[FeatureData])
final case class FeatureData(feature: Feature, nrOfUses: Int, lastUsed: Long)
