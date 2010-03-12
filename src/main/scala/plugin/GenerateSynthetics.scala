package com.googlecode.avro

import scala.tools._
import nsc.Global
import nsc.Phase
import nsc.plugins.Plugin
import nsc.plugins.PluginComponent
import nsc.transform.Transform
import nsc.transform.InfoTransform
import nsc.transform.TypingTransformers
import nsc.symtab.Flags._
import nsc.util.Position
import nsc.util.NoPosition
import nsc.ast.TreeDSL
import nsc.typechecker
import scala.annotation.tailrec

import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.apache.avro.Schema.{Type => AvroType}

import scala.collection.JavaConversions._

import scala.collection.mutable.ListBuffer

class GenerateSynthetics(plugin: ScalaAvroPlugin, val global : Global) extends PluginComponent
  with Transform
  with TypingTransformers
  with TreeDSL
{
  import global._
  import definitions._
  	  
  val runsAfter = List[String]("earlytyper")
  val phaseName = "generatesynthetics"
  def newTransformer(unit: CompilationUnit) = new ScalaAvroTransformer(unit)    

  class ScalaAvroTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {
    import CODE._
    import Helpers._

    val primitiveClasses = Map(
        IntClass     -> Schema.create(AvroType.INT),
        FloatClass   -> Schema.create(AvroType.FLOAT),
        LongClass    -> Schema.create(AvroType.LONG),
        DoubleClass  -> Schema.create(AvroType.DOUBLE),
        BooleanClass -> Schema.create(AvroType.BOOLEAN),
        StringClass  -> Schema.create(AvroType.STRING)
    )

    def generateSetMethod(templ: Template, clazz: Symbol, instanceVars: List[Symbol]) = {
        val newSym = clazz.newMethod(clazz.pos.focus, newTermName("put"))
        newSym setFlag SYNTHETICMETH 
        newSym setInfo MethodType(newSym.newSyntheticValueParams(List(IntClass.tpe, AnyClass.tpe)), UnitClass.tpe)
        clazz.info.decls enter newSym 

        //val idx = newSym ARG 0
        //idx.tpe = IntClass.tpe
        //val obj = newSym ARG 1
        //obj.tpe = AnyClass.tpe
        
        //newSym.info.decls enter idx
        //newSym.info.decls enter obj

        val default = List(DEFAULT ==> THROW(IndexOutOfBoundsExceptionClass, newSym ARG 0))

        println("-------")
        val list = clazz.info.decls.toList filter (_ hasFlag ACCESSOR)
        println(list)
        println("name list: " + list.map(_.name.toString))

        println("-----")
        println(unit.depends)
        println(unit.defined)

        println(unit.depends.map(s => (s.fullNameString,s.tpe.normalize.toString)))

        // TODO: find a better way to get a handle to these types
        val byteBufferTpe = unit.depends.filter(i => i.fullNameString == "java.nio.ByteBuffer" && !i.isModuleClass).head.tpe
        val utf8Tpe = unit.depends.filter(i => i.fullNameString == "org.apache.avro.util.Utf8" && !i.isModuleClass).head.tpe

        println("utf8Tpe: " + utf8Tpe)

        val cases = for ((sym, i) <- instanceVars.zipWithIndex) yield {
            //val castType = boxedMap get sym.tpe getOrElse sym.tpe
            //val cast = obj AS sym.tpe
            //cast match {
            //    case TypeApply(Select(qual, _), List(targ)) =>
            //        println("qual: " + qual)
            //        println("targ: " + targ)
            //        println("qual.tpe: " + qual.tpe)
            //        println("targ.tpe: " + targ.tpe)
            //        println("---")
            //    case _ => println("no match")
            //}
            

            val accessors = list.filter(_.name.toString.trim equals (sym.name.toString.trim + "_$eq"))
            println("accessor: " + accessors)
            //println("target.class" + target.getClass)

            //val target = Assign(This(clazz) DOT sym, cast)

            val rhs = 
                if (sym.tpe.typeSymbol == StringClass) {
                    typer typed ((newSym ARG 1) AS utf8Tpe DOT newTermName("toString"))
                } else if (sym.tpe.typeSymbol == ArrayClass && sym.tpe.normalize.typeArgs.head == ByteClass.tpe) {
                    typer typed ((newSym ARG 1) AS byteBufferTpe.normalize DOT newTermName("array"))
                } else {
                    typer typed ((newSym ARG 1) AS sym.tpe)
                }
            //val target = Apply(This(clazz) DOT accessors(0), List(typedDot))
            val target = Assign(This(clazz) DOT sym, rhs)
            //target match {
            //    case Apply(fn, args) =>
            //        if (fn.symbol == Any_asInstanceOf)
            //            fn match {
            //                case TypeApply(Select(qual, _), List(targ)) =>
            //                    println("qual: " + qual)
            //                    println("targ: " + targ)
            //                    println("qual.tpe: " + qual.tpe)
            //                    println("targ.tpe: " + targ.tpe)
            //                    println("---")
            //                case _ => println("no match in type apply")
            //            }
            //        else
            //            println("not an any instance of")
            //    case _ => println("No match in target")
            //}

            CASE(LIT(i)) ==> target
        }

        println(boxedClass)
        println(cases)

        val deffer = localTyper.typed {
            DEF(newSym) === {
                (newSym ARG 0) MATCH { cases ::: default : _* }
            }   
        }   

        deffer
    }

    def string2utf8(clazz: Symbol, sym: Symbol): Tree = {
        //val utf8 = unit.depends.toList.filter(_.name.toString.trim.equals("Utf8")).head
        //val utf8 = clazz.info.decls.iterator.toList.filter(_.name.toString.trim.equals("Utf8")).head
        //println("Found Utf8?: " + utf8)

        Apply(
            //Select(New(utf8, newTermName("<init>"))),
            This(clazz) DOT newTermName("mkUtf8"),
            List(This(clazz) DOT sym))
        //NEW(utf8, This(clazz) DOT sym)
    }

    def byteArray2byteBuffer(clazz: Symbol, sym: Symbol): Tree = {
        Apply(
            This(clazz) DOT newTermName("mkByteBuffer"),
            List(This(clazz) DOT sym))
    }

    def listToGenericArray(clazz: Symbol, sym: Symbol): Tree = {
        Apply(
            This(clazz) DOT newTermName("scalaListToGenericArray"),
            List(This(clazz) DOT sym)) 
    }

    def sym2ident(clazz: Symbol, sym: Symbol): Tree = This(clazz) DOT sym AS ObjectClass.tpe

    // TODO: don't use strings as keys, find a more general solution here
    private var symMap = Map(
            StringClass -> ((clazz:Symbol, sym:Symbol) => string2utf8(clazz,sym)),
            ArrayClass  -> ((clazz:Symbol, sym:Symbol) => 
                if (sym.tpe.typeArgs.head.typeSymbol == ByteClass)
                    byteArray2byteBuffer(clazz,sym)
                else
                    throw new UnsupportedOperationException("Cannot handle this right now")
                ),
            ListClass   -> ((clazz:Symbol, sym:Symbol) =>  listToGenericArray(clazz,sym))
        )

    def generateGetMethod(templ: Template, clazz: Symbol, instanceVars: List[Symbol]) = {
        val newSym = clazz.newMethod(clazz.pos.focus, newTermName("get"))
        newSym setFlag SYNTHETICMETH | OVERRIDE 
        newSym setInfo MethodType(newSym.newSyntheticValueParams(List(/*Boxed*/ IntClass.tpe)), /*Any*/ObjectClass.tpe)
        clazz.info.decls enter newSym 

        //val arg = (newSym ARG 0) DOT newTermName("intValue")
        val arg = newSym ARG 0
        val default = List(DEFAULT ==> THROW(IndexOutOfBoundsExceptionClass, arg))
        println(symMap)
        val cases = for ((sym, i) <- instanceVars.zipWithIndex) yield {
            //val castType = boxedMap get sym.tpe getOrElse sym.tpe
            //val target = This(clazz) DOT sym AS castType
            //val typedSymTree = localTyper typed { Ident(sym) }
            //println("sym.tpe: " + typedSymTree.symbol.tpe.toString)
            println("sym.tpe.normalize" + sym.tpe.normalize)
            println("sym.tpe.finalResultType" + sym.tpe.finalResultType)
            CASE(LIT(i)) ==> {
                val fn = symMap get (sym.tpe.typeSymbol) getOrElse ( (c:Symbol, s:Symbol) => sym2ident(c,s) )
                fn(clazz, sym)
            }
        }

        println(boxedClass)
        println(cases)

        val deffer = localTyper.typed {
            DEF(newSym) === {
                arg MATCH { cases ::: default : _* }
            }   
        }   

        deffer
    }

    def createSchemaFieldVal(name: Name, templ: Template, clazz: Symbol, instanceVars: List[Symbol]) = {
        println("name: " + name)
        println("clazz.name: " + clazz.name)
        println("clazz.owner.name: " + clazz.owner.name)
        println("clazz.owner.fullNameString: " + clazz.owner.fullNameString)
        println("clazz.fullNameString: " + clazz.fullNameString) 


        val newRecord = Schema.createRecord(clazz.name.toString, "Auto-Generated Schema", clazz.owner.fullNameString, false)

        val fieldList = instanceVars.map( iVar => {
            val fieldSchema = 
                if (primitiveClasses.get(iVar.tpe.typeSymbol).isDefined)
                    primitiveClasses.get(iVar.tpe.typeSymbol).get
                else if (iVar.tpe.typeSymbol == ArrayClass) {
                    println("iVar.tpe.normalize.typeArgs.head: " + iVar.tpe.normalize.typeArgs.head) 
                    // TODO: support array just as we support lists, with
                    // Array[Byte] as a sole exception (we should treat
                    // Array[Array[Byte]] as an array of byte buffers, 
                    // Array[Array[Array[Byte]]] as an array of arrays of byte
                    // buffers, and so on)
                    if (iVar.tpe.normalize.typeArgs.head != ByteClass.tpe)
                        throw new UnsupportedOperationException("Bad Array Found: " + iVar.tpe)
                    Schema.create(AvroType.BYTES)
                } else if (iVar.tpe.typeSymbol == ListClass) {
                    // TODO: we should handle this more recursively,
                    // so we can have List[List[T]] and so on, with
                    // Array[Byte] as an exception. We should also be able to
                    // mix arrays and lists, ie List[Array[List[Array[Byte]]]]
                    // should "do the right thing", which in this case is
                    // a list of list of list of byte buffers (Avro does not
                    // distinguish between lists/arrays)
                    val listParam = iVar.tpe.normalize.typeArgs.head 
                    if (primitiveClasses.get(listParam.typeSymbol).isDefined)
                        Schema.createArray(primitiveClasses.get(listParam.typeSymbol).get) 
                    else if (listParam.typeSymbol == ArrayClass && listParam.typeArgs.head.typeSymbol == ByteClass)
                        Schema.createArray(Schema.create(AvroType.BYTES))
                    else if (plugin.state.recordClassSchemas.get(listParam.normalize.toString).isDefined)
                        Schema.createArray(plugin.state.recordClassSchemas.get(listParam.normalize.toString).get)
                    else
                        throw new IllegalArgumentException("Cannot add non avro record list instance")
                } else if (plugin.state.recordClassSchemas.get(iVar.tpe.normalize.toString).isDefined) {
                    plugin.state.recordClassSchemas.get(iVar.tpe.normalize.toString).getOrElse(throw new IllegalStateException("should not be null"))
                } else 
                    throw new UnsupportedOperationException("Cannot support yet: " + iVar.tpe)

            println("fieldSchema: " + fieldSchema)

            new Field(
                iVar.name.toString.trim,
                fieldSchema,
                "Auto-Generated Field",
                null /* TODO: default values */
            )
        })
    
        val b = new ListBuffer[Field]
        b.appendAll(fieldList.iterator)
        newRecord.setFields(b)
        println("newRecord: " + newRecord.toString)
        //assert( plugin.state.contains(clazz.fullNameString) )
        plugin.state.recordClassSchemas += clazz.fullNameString -> newRecord

        val newSym = clazz.newValue(clazz.pos.focus, newTermName("_schema"))
        newSym setFlag PRIVATE 
        newSym setInfo ConstantType(Constant(newRecord.toString)) 
        clazz.info.decls enter newSym 

        localTyper typed {
            VAL(newSym) === LIT(newRecord.toString)
        }
    } 
   
    override def transform(tree: Tree) : Tree = {
        def isAnnotatedSym(sym: Symbol) = {	 	
            if (sym != null) {
                val testSym = if (sym.isModule) sym.moduleClass else sym
                testSym.annotations exists { _.toString == plugin.avroRecordAnnotationClass }
            } else false
        }

        def isAccessor(tree: Tree) = tree match {
            case m:MemberDef if m.mods.isAccessor => true
            case _ => false
        }

        def isInstanceVar(tree: Tree) = tree match {
            case v:ValDef => true            
            case _ => false
        }
		   
	  val newTree = tree match {
        case md @ PackageDef(pid, stats) =>
            println("PackageDef: " + md.name)
        
            val avroClassDefs = stats.filter { s =>
                s match {
                    case ClassDef(_,_,_,_) => isAnnotatedSym(s.symbol) 
                    case _ => false
                }
            }

            // Create a topological sorting of the class defs based on their 
            // schema dependencies. If we find that the dependency graph
            // contains a cycle, we'll throw an exception because avro
            // does not let you do this (try it, it doesn't work)

            // TODO: use something better than strings here for class names
            // (like the class symbol or type)
            val dependencyGraph = new DirectedGraph[String]

            avroClassDefs.foreach( classDef => dependencyGraph.add(classDef.symbol.fullNameString) )
            avroClassDefs.foreach( classDef => plugin.state.recordClassSchemas += (classDef.symbol.fullNameString -> null) )
            avroClassDefs.foreach( classDef => {
                println("avroClassDef: " + classDef)
                val instanceVars = classDef.asInstanceOf[ClassDef].impl.body.filter { isInstanceVar(_) }
                println("instanceVars: " + instanceVars)
                val avroInstanceVars = instanceVars.filter { iv => dependencyGraph.contains(iv.symbol.tpe.normalize.toString) }
                println("avroInstanceVars: " )
                avroInstanceVars.foreach( iv => dependencyGraph.addEdge(classDef.symbol.fullNameString, iv.symbol.tpe.normalize.toString) )
            })

            println("DepGraph: " + dependencyGraph)
            if (dependencyGraph.hasCycle)
                throw new IllegalStateException("Oops, your records have a cyclic dependency. Please resolve")
            val tSort = dependencyGraph.topologicalSort
            println("TSort: " + tSort)

            // TODO: this isn't very efficient
            val sortedAvroClassDefs = tSort.map( className => avroClassDefs.filter( classDef => classDef.symbol.fullNameString == className ).head )

            val newAvroStats = for (m <- sortedAvroClassDefs) yield {
                m match {
                    case cd @ ClassDef(mods, name, tparams, impl) =>
                        println("ClassDef: " + cd.name)
                        if (!isAnnotatedSym(m.symbol)) {
                            throw new IllegalStateException("should never happen")
                        } else {
                            val instanceVars = for (member <- impl.body if isInstanceVar(member)) yield { member.symbol }
                            println("instanceVars: " + instanceVars)
                            val newMethods = List(
                                    //generateDefaultCtor(impl, cd.symbol, instanceVars),
                                    generateGetMethod(impl, cd.symbol, instanceVars),
                                    generateSetMethod(impl, cd.symbol, instanceVars))

                            val schemaField = createSchemaFieldVal(name, impl, cd.symbol, instanceVars)
                            val newImpl = treeCopy.Template(impl, /*List(specificRecordBase, specificRecord) ::: */impl.parents, impl.self,  List(schemaField) ::: newMethods ::: impl.body)
                            treeCopy.ClassDef(m, mods, name, tparams, newImpl)
                        }
                }
            }

            val newPackageStats = for (m <- stats) yield {
                m match {
                    case cd @ ClassDef(mods, name, tparams, impl) =>
                        println("ClassDef: " + cd.name)
                        if (!isAnnotatedSym(m.symbol)) {
                            m
                        } else {
                            newAvroStats.filter(cd => m.symbol.fullNameString == cd.symbol.fullNameString).head
                        }
                    case _ => m
                }
            }
            println("newPackageStats: " + newPackageStats)
            treeCopy.PackageDef(md, pid, newPackageStats)
        case _ => tree
	  }
	  super.transform(newTree)
	}    
  }
}
