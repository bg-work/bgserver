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

package work.bg.server.core.mq

import work.bg.server.core.constant.ModelReservedKey

class ModelDataObject(override var data: FieldValueArray = FieldValueArray(),
    model:ModelBase?=null,fields:ArrayList<FieldBase>?=null):ModelData(data,model,fields) {
    val idFieldValue:FieldValue?
        get()= data.firstOrNull{
            it.field.name== ModelReservedKey.idFieldName
        }

    override fun isObject(): Boolean {
        return true
    }


    fun setFieldValue(propertyName:String,value:String?){
        var fieldValue = this.data.firstOrNull {
            it.field.propertyName==propertyName
        }
        if(fieldValue!=null) {
            var fValue= if(value!=null) ModelFieldConvert.toTypeValue(fieldValue.field,value) else null
            this.data.setValue(fieldValue.field,fValue)
        }
        else {
            var field = this.model?.fields?.getFieldByPropertyName(propertyName)
            if(field!=null){
                this.data.add(FieldValue(field,if(value!=null) ModelFieldConvert.toTypeValue(field,value) else null))
                this.fields?.add(field)
            }
        }
    }

    fun setFieldValue(field:FieldBase,value:Any?){
        var fieldValue = this.data.firstOrNull {
            it.field.isSame(field)
        }
        if(fieldValue!=null) {
           this.data.setValue(fieldValue.field,value)
        }
        else {
            this.data.add(FieldValue(field,value))
            this.fields?.add(field)
        }
    }

    fun hasFieldValue(propertyName:String):Boolean{
        return this.data.firstOrNull() {
                    it.field.propertyName == propertyName
                }!=null
    }
    fun hasFieldValue(field:FieldBase):Boolean{
        return this.data.firstOrNull() {
            it.field.isSame(field)
        }!=null
    }
    fun removeFieldValue(propertyName:String){
        this.data.removeIf{
            it.field.propertyName==propertyName
        }
        this.fields?.removeIf {
            it.propertyName==propertyName
        }
    }
    fun removeFieldValue(field:FieldBase){
        this.data.removeIf{
            it.field.isSame(field)
        }
        this.fields?.removeIf {
            it.isSame(field)
        }
    }
}