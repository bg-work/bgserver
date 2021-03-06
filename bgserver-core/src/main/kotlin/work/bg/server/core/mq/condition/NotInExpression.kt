

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

package work.bg.server.core.mq.condition

import org.apache.tomcat.util.modeler.modules.ModelerSource
import org.springframework.ui.Model
import work.bg.server.core.mq.FieldBase
import work.bg.server.core.mq.ModelExpression
import work.bg.server.core.mq.ModelExpressionVisitor

class NotInExpression(val field:FieldBase,val valueSet:Array<Any>?,val criteria:ModelExpression?=null):ModelExpression(){
    constructor(field:FieldBase,criteria:ModelExpression?):this(field,null,criteria){

    }
    override fun accept(visitor: ModelExpressionVisitor,parent:ModelExpression?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun render(parent:ModelExpression?): Pair<String, Map<String, Any?>>? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}