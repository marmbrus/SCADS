package edu.berkeley.cs.scads.piql.parser

import scala.collection.JavaConversions._
import org.apache.log4j.Logger

import org.apache.avro.Schema
import org.apache.avro.Schema.{Field, Type}

import scala.collection.mutable.HashMap

import edu.berkeley.cs.scads.piql._

object ScalaGen extends Generator[BoundSpec] {
	val logger = Logger.getLogger("scads.scalagen")
	val autogenWarning = "/* This is an autogenerated scads spec file, do not modify it unless you know what you are doing */"
	val imports = Array("import edu.berkeley.cs.scads.model")

	protected def generate(spec: BoundSpec)(implicit sb: StringBuilder, indnt: Indentation): Unit = {
		/* Headers */
		output("/* This is an autogenerated scads spec file, do not modify it unless you know what you are doing */")
    output("package piql") /** Hack for now, to avoid default namespace */
		output("import edu.berkeley.cs.scads.piql.{Entity, EntityPart, QueryExecutor, Environment, AttributeCondition}")
		output("import edu.berkeley.cs.scads.piql.parser.{BoundValue, BoundTrueValue, BoundFalseValue}")
    output("import edu.berkeley.cs.scads.storage.{ScadsCluster, Namespace}")
		output("import org.apache.avro.Schema")
		output("import org.apache.avro.util.Utf8")
		output("import org.apache.avro.specific.SpecificRecordBase")
		output("import scala.collection.mutable.HashMap")

		spec.entities.valuesIterator.foreach(generate)

    outputBraced("object Queries extends QueryExecutor") {
      spec.orphanQueries.foreach((generateQuery _).tupled)
    }

    generateConfigurator(spec.entities.valuesIterator.toList)
	}

  protected def generateConfigurator(entities: List[BoundEntity])(implicit sb: StringBuilder, indnt: Indentation) {

    def mkKeyType(entity: BoundEntity) = entity.name + ".KeyType"
    def mkValueType(entity: BoundEntity) = entity.name + ".ValueType"

    outputBraced("object Configurator") {
      outputBraced("def configure(c: ScadsCluster): Environment = ") {
        output("val env = new Environment")
        outputPartial("env.namespaces = Map(")
        entities.zipWithIndex.foreach( t => {
          val entity = t._1
          val namespace = entity.namespace
          val idx = t._2
          if (idx != 0)
            outputPartial(indentChar)
          outputPartialCont(quote(namespace), " -> ", "c.getNamespace[", mkKeyType(entity), ",", mkValueType(entity), "](", quote(namespace), ")")
          outputPartialCont(".asInstanceOf[Namespace[SpecificRecordBase,SpecificRecordBase]]")
          if (idx != entities.size - 1)
            outputPartialCont(",\n")
        })
        outputPartialCont(")")
        outputPartialEnd
        output("env")
      }
    }
  }

  protected def getFields(r: Schema, prefix: List[String]): List[(String, Type)] = {
    r.getFields.flatMap(f => f.schema().getType match {
      case Type.STRING | Type.BOOLEAN | Type.INT => {
        List(((prefix ::: List(f.name)).mkString("."), f.schema().getType))
      }
      case Type.RECORD => getFields(f.schema, prefix ::: List(f.name))
    }).toList
  }

  protected def outputFields(r: Schema)(implicit sb: StringBuilder, indnt: Indentation): Unit = {
    r.getFields.foreach(f => f.schema().getType match {
      case Type.STRING => output("var ", f.name, ":String = \"\"")
      case Type.BOOLEAN =>output("var ", f.name, ":Boolean = false")
      case Type.INT =>output("var ", f.name, ":Int = 0")
      case Type.RECORD =>
    })
  }

  protected def outputFunctions(r: Schema)(implicit sb: StringBuilder, indnt: Indentation): Unit = {
    val fields = r.getFields.toList

    outputBraced("override def get(f: Int): Object =") {
      outputBraced("f match ") {
        fields.zipWithIndex.foreach {
          case (field: Schema.Field, idx: Int) if(field.schema.getType == Type.INT) =>
            output("case ", idx.toString, " => new java.lang.Integer(", field.name, ")")
          case (field: Schema.Field, idx: Int) if(field.schema.getType == Type.BOOLEAN) =>
            output("case ", idx.toString, " => boolean2Boolean(", field.name, ")")
          case (field: Schema.Field, idx: Int) if(field.schema.getType == Type.STRING) =>
            output("case ", idx.toString, " => new Utf8(", field.name, ")")
          case (field: Schema.Field, idx: Int) =>
            output("case ", idx.toString, " => ", field.name)
        }
        output("case _ => throw new org.apache.avro.AvroRuntimeException(\"Bad index\")")
      }
    }

    outputBraced("override def get(f: String): Any =") {
      outputBraced("f match ") {
        fields.zipWithIndex.foreach { 
          case (field: Schema.Field, idx: Int) => 
            output("case ", quote(field.name), " => get(", idx.toString, ")")
        }
        output("case _ => throw new org.apache.avro.AvroRuntimeException(\"Bad field name: \" + f)")
      }
    }

    outputBraced("override def put(f: Int, v: Any): Unit =") {
      outputBraced("f match") {
        fields.zipWithIndex.foreach {
          case (field: Schema.Field, idx: Int) if(field.schema.getType == Type.INT) =>
            output("case ", idx.toString, " => ", field.name, " = v.asInstanceOf[java.lang.Integer].intValue")
          case (field: Schema.Field, idx: Int) if(field.schema.getType == Type.BOOLEAN) =>
            output("case ", idx.toString, " => ", field.name, " = v.asInstanceOf[java.lang.Boolean].booleanValue")
          case (field: Schema.Field, idx: Int) if(field.schema.getType == Type.STRING) =>
            output("case ", idx.toString, " => ", field.name, " = v.toString")
          case (field: Schema.Field, idx: Int) =>
            output("case ", idx.toString, " => ", field.name, ".parse(v.asInstanceOf[SpecificRecordBase].toBytes)")
        }
        output("case _ => throw new org.apache.avro.AvroRuntimeException(\"Bad index\")")
      }
    }

    outputBraced("override def put(f: String, v: Any): Unit =") {
      outputBraced("f match ") {
        fields.zipWithIndex.foreach {
          case (field: Schema.Field, idx: Int) => 
            output("case ", quote(field.name), " => put(", idx.toString, ", v)")
        }
        output("case _ => throw new org.apache.avro.AvroRuntimeException(\"Bad field name: \" + f)")
      }
    }

  }


	protected def generate(entity: BoundEntity)(implicit sb: StringBuilder, indnt: Indentation): Unit = {
		outputBraced("class ", entity.name, " extends Entity[", entity.name, ".KeyType", ",", entity.name, ".ValueType] with QueryExecutor") {
      output("val namespace = ", quote("ent_" + entity.name))

      output("object key extends ", entity.name, ".KeyType")
      output("object value extends ", entity.name, ".ValueType")

      output("val indexes: Map[String, Schema] = null")

      outputBraced("override def get(fieldName: String): Any =") {
        outputBraced("fieldName match ") {
          (entity.keySchema.getFields.toList ++ entity.valueSchema.getFields).foreach(field => {
              output("case ", quote(field.name), " => ", field.name)
          })
          output("case _ => throw new org.apache.avro.AvroRuntimeException(\"Bad field name: \" + fieldName)")
        }
      }

      def mkClazzName(t: Type): String = t match {
          case Type.INT => "Int"
          case Type.BOOLEAN => "Boolean"
          case Type.STRING => "String"
          case Type.RECORD => "SpecificRecordBase"
          case e => 
            logger.fatal("Invalid field schema type: " + e)
            throw new IllegalArgumentException("Invalid field schema type: " +e)
      }

      /** TODO: is there a better way to get this class name? */
      def stripKey(s: String) = s.substring(0, s.length - 3)
      def mkKeyType(s: String) = stripKey(s) + ".KeyType"
      def mkValueType(s: String) = stripKey(s) + ".ValueType"

      sealed trait KeyValueType
      object KeyType extends KeyValueType
      object ValueType extends KeyValueType

      def mkType(t: KeyValueType, s: String) = 
        if (t == KeyType)
          mkKeyType(s)
        else if (t == ValueType)
          mkValueType(s)
        else
          throw new IllegalArgumentException("Bad KeyValueType: " + t)

      def mkStrRep(t: KeyValueType) = 
        if (t == KeyType)
          "key"
        else if (t == ValueType)
          "value"
        else
          throw new IllegalArgumentException("Bad KeyValueType: " + t)

      def mkCaseMatches(fields: List[Schema.Field], t: KeyValueType) {
        fields.foreach(field => {
          if (field.schema.getType == Type.RECORD) {
            outputBraced("case ", quote(field.name), " => fieldValue$ match ") {
              System.err.println("field.schema.getName = " + field.schema.getName)
              output("case e$: ", stripKey(field.schema.getName), " => ", field.name, " = e$.", mkStrRep(t))
              output("case f$: ", mkType(t, field.schema.getName), " => ", field.name, " = f$")
              output("case _ => throw new IllegalArgumentException(\"Bad object type for field " + field.name + "\")")
            }
          } else 
            output("case ", quote(field.name), " => ", field.name, " = fieldValue$.asInstanceOf[" + mkClazzName(field.schema.getType) + "]")
        })
      }

      outputBraced("override def put(fieldName$: String, fieldValue$: Any): Unit =") {
        outputBraced("fieldName$ match ") {
          mkCaseMatches(entity.keySchema.getFields.toList, KeyType)
          mkCaseMatches(entity.valueSchema.getFields.toList, ValueType)
          output("case _ => throw new org.apache.avro.AvroRuntimeException(\"Bad field name: \" + fieldName$)")
        }
      }

      entity.keySchema.getFields.foreach(f => output("def ", f.name, " = key.", f.name))
      entity.valueSchema.getFields.foreach(f => output("def ", f.name, " = value.", f.name))

      def mkSetter(fields: List[Schema.Field], t: KeyValueType) {
        val prefix = mkStrRep(t)
        fields.foreach(f => {
          f.schema.getType match {
            case Type.INT | Type.BOOLEAN | Type.STRING =>
              outputBraced("def ", f.name, "_=(v$: ", mkClazzName(f.schema.getType), "): Unit =") {
                output(prefix, ".", f.name, " = v$") 
              }
            case Type.RECORD =>
              outputBraced("def ", f.name, "_=(v$: ", stripKey(f.schema.getName), "): Unit =") {
                output(f.name, " = v$.", prefix)
              }
              outputBraced("def ", f.name, "_=(v$: ", mkType(t, f.schema.getName), "): Unit =") {
                output(prefix, ".", f.name, ".parse(v$.toBytes)")
              }
            case e => 
              logger.fatal("Bad field name: " + f)
              throw new IllegalArgumentException("Bad field name: " + f)
          }
        })
      }

      mkSetter(entity.keySchema.getFields.toList, KeyType)
      mkSetter(entity.valueSchema.getFields.toList, ValueType)

      entity.queries.foreach((generateQuery _).tupled)
    }

    outputBraced("object ", entity.name) {
			def outputObjects(r: Schema, name: String, prefix: Option[String]) {
				val fields = r.getFields.toList
				fields.filter(_.schema.getType == Type.RECORD).foreach(f => outputObjects(f.schema, f.name, Some(prefix.getOrElse("") + f.name)))

				outputBraced("class ", prefix.getOrElse(name), "Type extends EntityPart") {
					output("def getSchema(): Schema = Schema.parse(\"\"\"", r.toString, "\"\"\")")
          outputFields(r)

          output("def flatValues = List(", getFields(r, List()).map(_._1).mkString(","), ")")

          outputBraced("def flatPut(idx: Int, v:Any): Unit =") {
            outputBraced("idx match") {
              getFields(r, List()).zipWithIndex.foreach {
                case ((f: String, t: Type), idx: Int) if(t == Type.INT) =>
                  output("case ", idx.toString, " => ", f, " = v.asInstanceOf[java.lang.Integer].intValue")
                case ((f: String, t: Type), idx: Int) if(t == Type.BOOLEAN) =>
                  output("case ", idx.toString, " => ", f, " = v.asInstanceOf[java.lang.Boolean].booleanValue")
                case ((f: String, t: Type), idx: Int) if(t == Type.STRING) =>
                  output("case ", idx.toString, " => ", f, " = v.toString")
              }
            }
          }

          val subparts: List[Schema.Field] = (entity.keySchema.getFields.toList ++ entity.valueSchema.getFields.toList).filter(_.schema.getType == Type.RECORD)
          subparts.foreach(p => {
            output("object ", p.name, " extends ", entity.name, ".", p.name, "Type")
          })

          outputFunctions(r)
				}
			}

			outputObjects(entity.keySchema, "Key", None)
			outputObjects(entity.valueSchema, "Value", None)
		}
	}

	protected def quote(string: String) = "\"" + string + "\""

  protected def argToCode(arg: Any): String = arg match {
    case c: Class[_] => "classOf[" + c.getName + "]"
    case s: String => "\"" + s + "\""
    case i: Int => i.toString
    case l: Seq[_] => "List(" + l.map(argToCode).mkString("", ", ", "") + ")"
    case h: HashMap[_, _] => "HashMap(" + h.map(p => "(" + argToCode(p._1) + ", " + argToCode(p._2) + ")").mkString("", ", ", "") + ").asInstanceOf[HashMap[String, BoundValue]]"
    case EntityClass(name) => "classOf[" + name + "].asInstanceOf[Class[Entity[_,_]]]"
    case true => "true"
    case false => "false"
    case BoundTrueValue => "BoundTrueValue"
    case BoundFalseValue => "BoundFalseValue"
    case BoundParameter(name, _) => name
    case BoundThisAttribute(name, _) => name
    case BoundIntegerValue(i) => i.toString
    case AttributeCondition(attr) => "AttributeCondition(" + quote(attr) + ")"
    case u: AnyRef => {
      logger.fatal("I don't know how to generate scala for argument of type: " + u.getClass + " => " + u)
      ""
    }
  }

  protected def toScalaType(s: Schema): String = s.getType() match {
    case Type.STRING => "String"
    case Type.BOOLEAN => "Boolean"
    case Type.INT => "Int"
  }

  protected def generateQuery(name: String, query: BoundQuery)(implicit sb: StringBuilder, indnt: Indentation) {
    val args = query.parameters.map((p) => {p.name + ":" + toScalaType(p.schema)}).mkString("", ",", "")

    output("def ", name, "(", args, ")(implicit env: Environment):Seq[", query.fetchTree.entity.name, "] = {")
    indent {
      output("qLogger.debug(\"Executing query '", name, "'\")")
      if(query.plan == null)
        output("null")
      else {
        output("val result =")
        generatePlan(query.plan)
        output("result.asInstanceOf[Seq[" + query.fetchTree.entity.name + "]]")
      }
    }
    output("}")
  }

  protected def generatePlan(plan: QueryPlan)(implicit sb: StringBuilder, indnt: Indentation):Unit = {
    val cl = plan.getClass
    val name = cl.getName.split("\\.").last
    val methodName = name.replaceFirst(name.slice(0,1), name.slice(0,1).toLowerCase)
    val fieldNames = cl.getDeclaredFields.reverse.map(_.getName)
    val fieldValues = fieldNames.filter(n => !(n equals "child")).map(cl.getMethod(_).invoke(plan)).toList
    val childValue: QueryPlan =
    if(fieldNames.contains("child"))
      cl.getMethod("child").invoke(plan).asInstanceOf[QueryPlan]
    else
      null
    outputPlan(methodName, fieldValues, childValue)
  }

  protected def outputPlan(func: String, args: List[Any], child: QueryPlan)(implicit sb: StringBuilder, indnt: Indentation):Unit = {
    outputPartial(func, "(")
    val argCode = args.map(argToCode)
    sb.append(argCode.mkString("", ", ", ""))

    if(child != null) {
      sb.append(",\n")
      indent {
        generatePlan(child)
      }
      output(")")
    }
    else
      sb.append(")\n")
  }
}
