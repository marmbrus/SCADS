package edu.berkeley.cs.scads.model.parser

object ScalaGen extends Generator[BoundSpec] {
	val autogenWarning = "/* This is an autogenerated scads spec file, do not modify it unless you know what you are doing */"
	val imports = Array("import edu.berkeley.cs.scads.model")
	protected def generate(spec: BoundSpec)(implicit sb: StringBuilder, indnt: Indentation): Unit = {
		/* Headers */
		output("/* This is an autogenerated scads spec file, do not modify it unless you know what you are doing */")
		output("import edu.berkeley.cs.scads.model._")
		output("object ScadsEnv extends Environment")

		/* Entities */
		spec.entities.foreach((e) => {
			output("class ", e._1, " extends Entity()(ScadsEnv) {")
			indent {
				/* Setup namespace and versioning system */
				output("val namespace = \"", Namespaces.entity(e._1), "\"")
				output("val version = new IntegerVersion()")

				/* Attribute holding objects */
				e._2.attributes.foreach((a) => {
					output("object ", a._1, " extends ", fieldType(a._2))
				})

				/* Attribute name to object map */
				output("val attributes = Map(")
				indent {
					val attrMap = e._2.attributes.keys.map((a) =>
						"(\"" + a + "\" -> " + a  +")").mkString("", ",\n", "")
					output(attrMap)

				}
				output(")")

				/* Index placeholder */
				output("val indexes = Array[Index]()")

				/* Primary Key */
				output("val primaryKey = ")
				indent {
					if(e._2.keys.size > 2)
						output("new CompositeKey(" + e._2.keys.mkString("", ",", ""), ")")
					else
						output(e._2.keys(0))
				}

			}
			output("}")

		})
	}

	private def fieldType(aType: AttributeType): String = {
		aType match {
			case BooleanType => "BooleanField"
			case StringType => "StringField"
			case IntegerType => "IntegerField"
		}
	}
}
