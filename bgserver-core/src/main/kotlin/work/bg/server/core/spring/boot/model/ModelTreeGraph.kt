/*
 *
 *  *
 *  *  * Copyright (c) Shanghai Xing Ye, Co. Ltd.
 *  *  * https://bg.work
 *  *  *
 *  *  * GNU Lesser General Public License Usage
 *  *  * Alternatively, this file may be used under the terms of the GNU Lesser
 *  *  * General Public License version 3 as published by the Free Software
 *  *  * Foundation and appearing in the file LICENSE.txt included in the
 *  *  * project of this file. Please review the following information to
 *  *  * ensure the GNU Lesser General Public License version 3 requirements
 *  *  * will be met: https://www.gnu.org/licenses/lgpl-3.0.html.
 *  *
 *
 */

package work.bg.server.core.spring.boot.model

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.annotation.AnnotationUtils
import work.bg.server.core.spring.boot.annotation.Action
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
//to do for test
class ModelTreeGraph constructor(val modelMetaDatas: List<ModelMetaData>):ModelGraph{
    private val logger = LoggerFactory.getLogger(ModelGraph::class.java)
    var root: ModelTreeGraphNode?=null
    override fun build(){
        this.root=this.buildGraphTree()
    }
    override fun createModelAction(): ModelAction?{
        var ams=this.createNodeAction(this.root!!)
        if(ams.count()>0){
            var ma=ModelAction()
            ams.forEach { t, u -> ma.addMethod(t,u)}
            return ma
        }
        return null
    }
    override fun getConcreteModelMetaDatas():List<ModelMetaData?> {
        var modelMetaDatas= mutableListOf<ModelMetaData?>()
        if (this.root!=null){
            visitGraphTree(this.root!!){
                modelMetaDatas.add(it.getModelMetaData())
            }
        }
        return modelMetaDatas
    }
    fun visitGraphTree(node: ModelTreeGraphNode,body:(n: ModelTreeGraphNode) -> Unit){
        body(node)
        node.getSubNodes().forEach { this.visitGraphTree(it,body) }
    }
    fun createNodeAction(node: ModelTreeGraphNode):Map<String,ActionMethod>{
        var ams= mutableMapOf<String,ActionMethod>()
        var subNodes=node.getSubNodes()
        subNodes.forEach {
            this.combineModelAction(ams,this.createNodeAction(it))
        }
        var mmd=node.getModelMetaData()
        if(mmd!=null){
            val mtyp=(mmd?.beanDefinitionHolder.beanDefinition as GenericBeanDefinition).beanClass.kotlin
            mtyp.memberFunctions.forEach{
                if (it.isOpen){
                    var ann= AnnotationUtils.findAnnotation(it.javaMethod, Action::class.java)
                    if(ann!=null){
                        if(!ams.containsKey(ann.name)){
                            ams.put(ann.name, ActionMethod(it))
                        }
                    }
                }
            }
        }
        return ams
    }

    fun combineModelAction(targetAms: MutableMap<String,ActionMethod>,sourceAms:Map<String,ActionMethod>){
        sourceAms.forEach { t, u ->
            if (targetAms.containsKey(t)){
                logger.warn("model action name ["+t+"] conflict!")
            }else{
                targetAms.put(t,u)
            }
        }
    }

    private  fun buildGraphTree(): ModelTreeGraphNode?{
        var nodes=this.buildAllGraphNodes()
        nodes.forEach root@{
            if (it.isRoot()){
                this.root=it
                return@root
            }
        }
        
        if(this.root!=null){
            nodes=nodes.filter {
                it!=this.root!!
            }
            fillSubNodes(this.root,nodes.toMutableList())
        }
        return this.root
    }
    private  fun buildAllGraphNodes():List<ModelTreeGraphNode>{
        var nodes= mutableListOf<ModelTreeGraphNode>()
        this.modelMetaDatas.forEach mm@{
            var hasCell=false
            var mmd=it
            nodes.forEach gn@ {
                if(gn@it.setModelMetaData(mm@mmd)){
                    hasCell=true
                }
            }
            if(!hasCell){
                var node=ModelTreeGraphNode(null)
                node.setModelMetaData(mm@mmd)
                nodes.add(node)
            }
        }
        return nodes
    }


    private fun fillSubNodes(parent:ModelTreeGraphNode?,nodes:MutableList<ModelTreeGraphNode>){
        var subNodes= mutableListOf<ModelTreeGraphNode>()
        nodes.forEach{
            if (it.isDependentedNode(parent!!)){
                parent?.addSubNode(it)
                subNodes.add(it)
            }
        }
        nodes.removeAll(subNodes)
        subNodes.forEach {
            fillSubNodes(it,nodes)
        }
    }
}
