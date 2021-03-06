

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

package work.bg.server.core.model

import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionDefinition
import work.bg.server.core.cache.PartnerCache
import work.bg.server.core.mq.*
import work.bg.server.core.mq.`in` as selectIn
import work.bg.server.core.mq.join.JoinModel
import work.bg.server.core.mq.join.leftJoin
import org.springframework.transaction.TransactionDefinition
import work.bg.server.core.acrule.ModelCreateFieldsValueFilterRule
import work.bg.server.core.acrule.ModelCreateRecordFieldsInitializeRule
import work.bg.server.core.acrule.ModelCreateRecordFieldsValueCheckControlRule
import work.bg.server.core.acrule.ModelCreateRecordFieldsValueCheckInStoreControlRule
import work.bg.server.core.acrule.bean.ModelCreateFieldsInspectorCheckBean
import work.bg.server.core.acrule.bean.ModelCreateFieldsSetIsolationFieldsBean
import work.bg.server.core.acrule.bean.PartnerModelCreateFieldsInStoreInspectorCheckBean
import work.bg.server.core.acrule.inspector.ModelFieldInspector
import work.bg.server.core.constant.ModelReservedKey
import work.bg.server.core.exception.ModelErrorException
import work.bg.server.core.mq.billboard.CurrCorpBillboard
import work.bg.server.core.mq.billboard.CurrPartnerBillboard
import work.bg.server.core.mq.billboard.FieldDefaultValueBillboard
import work.bg.server.core.mq.billboard.TimestampBillboard
import work.bg.server.core.mq.join.innerJoin
import work.bg.server.core.mq.specialized.ConstGetRecordRefField
import work.bg.server.core.mq.specialized.ConstRelRegistriesField
import work.bg.server.core.mq.specialized.ConstSetRecordRefField
import kotlin.streams.toList


abstract  class AccessControlModel(tableName:String,schemaName:String): ModelBase(tableName,schemaName) {

    private val logger = LogFactory.getLog(javaClass)

    @Value("\${ui.model.page-size}")
    private var pageSize:Int=30
    @Autowired
    protected lateinit var basePartner:BasePartner
    @Autowired
    protected  lateinit var baseCorp:BaseCorp
    @Autowired
    protected lateinit var basePartnerRole:BasePartnerRole

    @Autowired
    protected  lateinit var baseCorpPartnerRel:BaseCorpPartnerRel
    @Autowired
    protected lateinit var txManager:PlatformTransactionManager

    @Autowired
    protected  lateinit var createRecordSetIsolationFields: ModelCreateFieldsSetIsolationFieldsBean

    @Autowired
    protected lateinit var modelCreateFieldsInspectorCheck: ModelCreateFieldsInspectorCheckBean

    @Autowired
    protected lateinit var partnerModelCreateFieldsInStoreInspectorCheck: PartnerModelCreateFieldsInStoreInspectorCheckBean



    /*Corp Isolation Fields Begin*/
    val createTime=ModelField(null,"create_time",FieldType.DATETIME,"添加时间",defaultValue = TimestampBillboard(constant = true))
    val lastModifyTime=ModelField(null,"last_modify_time",FieldType.DATETIME,"最近修改时间",defaultValue = TimestampBillboard())
    val createPartnerID=ModelField(null,"create_partner_id",FieldType.BIGINT,"添加人",defaultValue = CurrPartnerBillboard(true))
    val lastModifyPartnerID=ModelField(null,"last_modify_partner_id",FieldType.BIGINT,"最近修改人",defaultValue = CurrPartnerBillboard())
    val createCorpID=ModelField(null,"create_corp_id",FieldType.BIGINT,"添加公司",defaultValue = CurrCorpBillboard(true))
    val lastModifyCorpID=ModelField(null,"last_modify_corp_id",FieldType.BIGINT,"最近修改公司",defaultValue = CurrCorpBillboard())
    /*Corp Isolation Fields End*/



    init {

    }
    open fun corpIsolationFields():Array<ModelField>?{
        if(this.skipCorpIsolationFields()){
            return null
        }

        return arrayOf(
                createTime,
                lastModifyTime,
                createPartnerID,
                lastModifyPartnerID,
                createCorpID,
                lastModifyCorpID
        )
    }

    override fun isAssociative():Boolean{
        return false
    }

    open fun maybeCheckACRule():Boolean{

        return true
    }


    fun acRead(vararg fields:FieldBase,
               model:ModelBase?=null,
               criteria:ModelExpression?,
               partnerCache:PartnerCache,
               orderBy:OrderBy?=null,
               pageIndex:Int?=null,
               pageSize:Int?=null,
               attachedFields:Array<AttachedField>?=null,
               relationPaging:Boolean=false):ModelDataArray?{

            if (model == null) return this.rawRead(*fields,
                model = this,
                criteria = criteria,
                orderBy = orderBy,
                pageIndex = pageIndex,
                pageSize = pageSize,
                attachedFields = attachedFields,
                relationPaging = relationPaging,
                useAccessControl = true,
                partnerCache = partnerCache)

            var acCriteria=null as ModelExpression?
            acCriteria=if(acCriteria!=null) {
                if(criteria!=null){
                    and(acCriteria,criteria)
                }
                else{
                    acCriteria
                }
            } else criteria

            return this.rawRead(*fields,
                    model = model,
                    criteria = acCriteria,
                    orderBy = orderBy,
                    pageIndex = pageIndex,
                    pageSize = pageSize,
                    attachedFields = attachedFields,
                    relationPaging = relationPaging,
                    useAccessControl = true,
                    partnerCache = partnerCache)

    }
    //todo rebuild criteria to remove redundant
    open fun smartReconcileCriteria(criteria:ModelExpression?):ModelExpression?{
        return criteria
    }
    open fun beforeRead(criteria:ModelExpression?,useAccessControl:Boolean,partnerCache: PartnerCache?=null):ModelExpression?{
        if (useAccessControl && partnerCache!=null){
            if(!this.skipCorpIsolationFields()){
                var readCriteria=eq(this.createCorpID,partnerCache.corpID)
                if(criteria!=null){
                    readCriteria=and(criteria,readCriteria!!)
                }
                return smartReconcileCriteria(readCriteria)
            }
        }
        else if(useAccessControl){
            throw ModelErrorException("权限错误")
        }
        return criteria
    }

    open fun rawRead(vararg fields:FieldBase,
                     model:ModelBase?=null,
                     criteria:ModelExpression?,
                     orderBy:OrderBy?=null,
                     pageIndex:Int?=null,
                     pageSize:Int?=null,
                     attachedFields:Array<AttachedField>?=null,
                     relationPaging:Boolean=false,
                     useAccessControl: Boolean=false,
                     partnerCache:PartnerCache?=null):ModelDataArray?{
        if(model==null){
            return this.rawRead(*fields,
                    model=this,
                    criteria = criteria,
                    orderBy = orderBy,
                    pageSize = pageSize,
                    pageIndex = pageIndex,
                    attachedFields = attachedFields,
                    relationPaging = relationPaging,
                    useAccessControl = useAccessControl,
                    partnerCache = partnerCache)
        }
        if(useAccessControl && partnerCache==null){
            return null
        }

        var fs=ArrayList<FieldBase>()
        var o2ofs=ArrayList<FieldBase>()
        var o2mfs=ArrayList<AttachedField>()
        var m2ofs=ArrayList<FieldBase>()
        var m2mfs=ArrayList<AttachedField>()
        if(fields.isEmpty()){
            var pFields= model?.fields?.getAllPersistFields()?.values?.toTypedArray()
            if(partnerCache!=null){
               // pFields=partnerCache.acFilterReadFields(pFields!!)
            }
            if(pFields!=null){
                pFields?.forEach {

                    when(it){
                        is One2OneField->{
                            o2ofs.add(it)
                        }
                        is One2ManyField->{
                            o2mfs.add(AttachedField(it))
                        }
                        is Many2OneField->{
                            m2ofs.add(it)
                        }
                        is Many2ManyField->{
                            m2mfs.add(AttachedField(it))
                        }
                        else->{
                          fs.add(it)
                        }
                    }
                }
            }
        }
        else{
            var pFields: Array<FieldBase>?=null
            pFields = if(useAccessControl){
               // partnerCache?.acFilterReadFields(fields as Array<FieldBase>)
                pFields
            } else fields as Array<FieldBase>?
            pFields?.forEach {
                when(it){
                        is One2OneField ->{
                            o2ofs.add(it)
                        }
                        is One2ManyField ->{
                            o2mfs.add(AttachedField(it))
                        }
                        is Many2OneField ->{
                            m2ofs.add(it)
                        }
                        is Many2ManyField->{
                            m2mfs.add(AttachedField(it))
                        }
                        else->{
                            fs.add(it)
                        }
                }
            }
        }
        var joinModels= arrayListOf<JoinModel>()
        var modelRelationMatcher = ModelRelationMatcher()
        o2ofs.forEach {
            var mf=this.getTargetModelField(it)
            if(mf!=null){
                if(useAccessControl){
                    var o2oFields=mf?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!//partnerCache?.acFilterReadFields(mf?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!)
                    if(o2oFields!=null){
                        fs.addAll(o2oFields)
                    }
                    else{
                        return@forEach
                    }
                }
                else{
                    fs.addAll(mf?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!)
                }
                var o2oFd= it as ModelOne2OneField
                if(o2oFd.isVirtualField){
                    var idf=o2oFd.model?.fields?.getIdField()
                    modelRelationMatcher.addMatchData(model,o2oFd,mf?.first,mf?.second,idf)
                    joinModels.add(leftJoin(mf?.first,eq(mf?.second!!,idf)!!))
                }
                else{
                    modelRelationMatcher.addMatchData(model,o2oFd,mf?.first,mf?.second)
                    joinModels.add(leftJoin(mf?.first,eq(mf?.second!!,it)!!))
                }
            }
        }

        m2ofs.forEach{
            var mf=this.getTargetModelField(it)
            if(mf!=null){
                if(useAccessControl){
                    var m2oFields=mf?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!//partnerCache?.acFilterReadFields(mf?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!)
                    if(m2oFields!=null){
                        fs.addAll(m2oFields)
                    }
                    else{
                        return@forEach
                    }
                }
                else{
                    fs.addAll(mf?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!)
                }
                modelRelationMatcher.addMatchData(model,it,mf?.first,mf?.second)
                joinModels.add(leftJoin(mf?.first,eq(mf?.second!!,it)!!))
            }
        }
        var offset=null as Int?
        var limit=null as Int?
        var newOrderBy=orderBy
        if(pageIndex!=null){
            limit= pageSize ?: this.pageSize
            offset=(pageIndex-1)*limit
            if(newOrderBy==null){
                newOrderBy=model?.fields?.getDefaultOrderBy()
            }
        }
        var readCriteria= beforeRead(criteria,useAccessControl,partnerCache)
        var mDataArray=this.query(*fs.toTypedArray(),fromModel = model!!,joinModels = joinModels.toTypedArray(),criteria = readCriteria,orderBy = newOrderBy,offset = offset,limit = limit)
        mDataArray?.model=model
        mDataArray=this.reconstructSingleRelationModelRecordSet(mDataArray,modelRelationMatcher)

        var rmfs= mutableMapOf<String,MutableList<AttachedField>>()
        m2mfs.forEach {
            val field=it.field as RefRelationField
            if(field.relationModelTable!=null){
                if(rmfs.containsKey(field.relationModelTable!!)){
                    rmfs[field.relationModelTable!!]?.add(it)
                }
                else{
                    var mlst= mutableListOf<AttachedField>()
                    mlst.add(it)
                    rmfs[field.relationModelTable!!]=mlst
                }
            }
        }

        var to2mfs=ArrayList<AttachedField>()
        to2mfs.addAll(o2mfs)

//        to2mfs.forEach {
//            var rrf=it as RefRelationField
//            if(rrf.relationModelTable!=null){
//                if(rmfs.containsKey(rrf.relationModelTable!!)){
//                    rmfs[rrf.relationModelTable!!]?.add(it)
//                }
//                else{
//                    var mLst= mutableListOf<RefRelationField>()
//                    mLst.add(it)
//                    rmfs[rrf.relationModelTable!!]=mLst
//                }
//                o2mfs.remove(it)
//            }
//        }


        attachedFields?.forEach {
            if(it.field is Many2ManyField){
                var rrf=it.field as RefRelationField
                if(rrf.relationModelTable!=null){
                    if(rmfs.containsKey(rrf.relationModelTable!!)){
                        var fList=rmfs[rrf.relationModelTable!!]
                        if(fList!!.filter {rt-> (rt.field as FieldBase).getFullName()==(rrf as FieldBase).getFullName() }.count()<1){
                            fList.add(it)
                        }
                        else{
                            fList.removeIf { xit ->
                                (xit.field as FieldBase).isSame(rrf as FieldBase)
                            }
                            fList.add(it)
                        }
                    }
                    else{
                        var mlst= mutableListOf<AttachedField>()
                        mlst.add(it)
                        rmfs[rrf.relationModelTable!!]=mlst
                    }
                }
            }
            else if(it.field is One2ManyField){
                var rtf=it as RefTargetField
                if(o2mfs.filter { rt-> (rt.field as FieldBase).getFullName()==(it.field as FieldBase).getFullName() }.count()<1)
                {
                    o2mfs.add(it)
                }
                else{
                    o2mfs.removeIf { xit ->
                        (xit.field as FieldBase).isSame(rtf as FieldBase)
                    }
                    o2mfs.add(it)
                }
            }
        }


        rmfs.forEach {
            modelRelationMatcher = ModelRelationMatcher()

            var rmf=this.getRelationModelField(it.value.first().field as FieldBase)
            var idField=model?.fields?.getIdField()
            var rIDField=rmf?.first?.fields?.getFieldByTargetField(idField)
            var subSelect=select(idField!!,fromModel = model!!).where(readCriteria).orderBy(newOrderBy).offset(offset).limit(limit)
            var rtFields=ArrayList<FieldBase>()
            rtFields.addAll(rmf?.first?.fields?.getAllPersistFields()?.values!!)
            modelRelationMatcher.addMatchData(model,idField,rmf?.first,rIDField)
            var joinModels=ArrayList<JoinModel>()
            it.value.forEach allField@{rrf->
                var sRmf=this.getRelationModelField(rrf.field as FieldBase)
                var targetMF=this.getTargetModelField(rrf.field as FieldBase)
                var jField=sRmf?.first?.fields?.getFieldByTargetField(targetMF?.second)
                if(useAccessControl){
                    //var rmfFields=partnerCache?.acFilterReadFields(sRmf?.first?.fields?.getAllPersistFields()?.values?.toTypedArray()!!)
                    var targetMFFields=targetMF?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!//partnerCache?.acFilterReadFields(targetMF?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!)
                    if(targetMFFields!=null){
                        //rtFields.addAll(rmfFields)
                        rtFields.addAll(targetMFFields)
                    }
                    else{
                        return@allField
                    }
                }else{
                    rtFields.addAll(targetMF?.first?.fields?.getAllPersistFields(true)?.values!!)
                }
                modelRelationMatcher.addMatchData(rmf?.first,sRmf?.second,targetMF?.first,targetMF?.second)
                if(rrf.canBeEmpty){
                    joinModels.add(leftJoin(targetMF?.first, eq(jField!!,targetMF?.second!!)!!))
                }
                else{
                    joinModels.add(innerJoin(targetMF?.first, eq(jField!!,targetMF?.second!!)!!))
                }
            }

            var rOrderBy=null as OrderBy?
            var rOffset=null as Int?
            var rLimit=null as Int?
            //todo add support pagesize every field
//            var selfField=it.value.first() as ModelField
//            if(relationPaging && (selfField is PagingField)){
//                rOrderBy=rmf?.first?.fields?.getDefaultOrderBy()
//                rOffset=0
//                rLimit=selfField.pageSize
//            }
            var attachedCriteriaArr=it.value.filter {
                af->
                af.criteria!=null
            }.stream().map { x->x.criteria }.toList()
            var subCriteria=selectIn(rIDField!!,subSelect)
            if(attachedCriteriaArr.count()>0){
                var mLst= mutableListOf<ModelExpression>()
                attachedCriteriaArr.forEach {mIt->
                    mLst.add(mIt!!)
                }
                mLst.add(subSelect)
                subCriteria=and(*mLst.toTypedArray())
            }
            var mrDataArray=this.query(*rtFields.toTypedArray(),
                    fromModel= rmf?.first!!,
                    joinModels=joinModels.toTypedArray(),
                    criteria=subCriteria,
                    orderBy = rOrderBy,
                    offset = rOffset,
                    limit = rLimit)
            var fieldArr=it.value.stream().map { x->x.field }.toList() as List<FieldBase>
            reconstructMultipleRelationModelRecordSet(model,
                    fieldArr.toTypedArray(),
                    mDataArray,rmf.first,
                    rtFields.toTypedArray(),
                    mrDataArray,
                    modelRelationMatcher)
        }


        o2mfs.forEach {
            modelRelationMatcher = ModelRelationMatcher()
            var targetMF=this.getTargetModelField(it.field as FieldBase)
            if(targetMF!=null){
                var subSelect=select(model?.fields?.getIdField()!!,fromModel = model).where(readCriteria).orderBy(newOrderBy).offset(offset).limit(limit)

                var rOrderBy=null as OrderBy?
                var rOffset=null as Int?
                var rLimit=null as Int?
                //todo add support pagesize every field
//                if(relationPaging && (it is PagingField)){
//                    rOrderBy=targetMF?.first?.fields?.getDefaultOrderBy()
//                    rOffset=0
//                    rLimit=it.pageSize
//                }
                if(useAccessControl){
                    var targetMFFields=targetMF?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!//partnerCache?.acFilterReadFields(targetMF?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!)
                    if(targetMFFields!=null){

                        modelRelationMatcher.addMatchData(model,it.field,targetMF?.first,targetMF?.second,model.fields?.getIdField())
                        var subCriteria=selectIn(targetMF?.second!!,subSelect)
                        if(it.criteria!=null){
                            subCriteria=and(subCriteria!!,it.criteria)
                        }
                        var mrDataArray=this.query(*targetMFFields,fromModel= targetMF?.first!!,criteria=subCriteria,
                                orderBy = rOrderBy,offset = rOffset,limit = rLimit)

                        reconstructMultipleRelationModelRecordSet(model,
                                arrayOf(it.field as FieldBase),
                                mDataArray,
                                null,
                                targetMFFields,
                                mrDataArray,
                                modelRelationMatcher)
                    }
                    else{
                        return@forEach
                    }
                }
                else{
                    modelRelationMatcher.addMatchData(model,it.field,targetMF?.first,targetMF?.second,model.fields?.getIdField())
                    var subCriteria=selectIn(targetMF?.second!!,subSelect)
                    if(it.criteria!=null){
                        subCriteria=and(subCriteria!!,it.criteria)
                    }
                    var mrDataArray=this.query(*targetMF?.first?.fields?.getAllPersistFields(true)?.values?.toTypedArray()!!,fromModel= targetMF?.first!!,criteria=subCriteria,
                            orderBy = rOrderBy,offset = rOffset,limit = rLimit)

                    reconstructMultipleRelationModelRecordSet(model,
                            arrayOf(it.field as FieldBase),
                            mDataArray,
                            null,
                            targetMF?.first?.fields?.getAllPersistFields()?.values?.toTypedArray()!!,
                            mrDataArray,
                            modelRelationMatcher)
                }
            }
        }
        return mDataArray
    }

    protected  open fun reconstructMultipleRelationModelRecordSet(model:ModelBase?,
                                                                  fields:Array<FieldBase>,
                                                                  reqMainArray:ModelDataArray?,
                                                                  relModel:ModelBase?,
                                                                  targetFields:Array<FieldBase>,
                                                                  relDataArray:ModelDataArray?,
                                                                  modelRelationMatcher: ModelRelationMatcher){

        //fix performance
        fields.forEach {
            reqMainArray?.fields?.add(it as FieldBase)
        }
        var sourceID2ModelDataArrayContainer= mutableMapOf<Long,LinkedHashMap<FieldBase?,ModelData>>()
        relDataArray?.data?.forEach r@{ relRecord->
            fields.forEach f@{fd->
                var keyField = if(relModel!=null){
                    relModel.fields?.getFieldByTargetField(model?.fields?.getIdField())
                }
                else
                {
                    var tf= this.getTargetModelField((fd as FieldBase))
                    tf?.first?.fields?.getFieldByTargetField(model?.fields?.getIdField())
                }

                if(keyField!=null){

                    var sourceId=relRecord.firstOrNull {
                        it.field.isSame(keyField)
                    }?.value as Long

                    var mrs=if(relModel!=null){
                        var fdMap= mutableMapOf<FieldBase,ModelData>()
                        var relModelDataArray=if(sourceID2ModelDataArrayContainer.containsKey(sourceId)
                                && sourceID2ModelDataArrayContainer[sourceId]?.containsKey(ConstRelRegistriesField.ref)!!
                        && (sourceID2ModelDataArrayContainer[sourceId]?.get(ConstRelRegistriesField.ref) as ModelDataSharedObject?)?.data!=null
                        && (sourceID2ModelDataArrayContainer[sourceId]?.get(ConstRelRegistriesField.ref) as ModelDataSharedObject?)?.data?.containsKey(relModel)!!){
                            (sourceID2ModelDataArrayContainer[sourceId]?.get(ConstRelRegistriesField.ref) as ModelDataSharedObject?)?.data?.get(relModel) as ModelDataArray
                        }
                        else {
                            var relModelDataArray = ModelDataArray(fields = arrayListOf(*relModel?.fields?.getAllPersistFields()?.values?.toTypedArray()!!), model = relModel)
                            var mrfd=modelRelationMatcher.getRelationMatchField(model,relModel)
                            relModelDataArray.fromIdValue=sourceId
                            relModelDataArray.fromField=mrfd?.fromField
                            relModelDataArray.toField=mrfd?.toField
                            relModelDataArray
                        }
                        //relRegistries field in main record
                        fdMap[ConstRelRegistriesField.ref]=ModelDataSharedObject(mutableMapOf(Pair(relModel,relModelDataArray)))

                        var nRec = FieldValueArray()
                        var mFields = arrayListOf<FieldBase>()

                        targetFields.forEach {
                            if (it.model == relModel) {
                                mFields.add(it)
                            }
                        }

                        mFields.forEach {
                            //var key = it.getFullName()!!
                            var mFV=relRecord.firstOrNull {mit->
                                mit.field.isSame(it)
                            }
                            if(mFV!=null){
                                nRec.add(mFV)
                            }
                        }

                        fields.forEach { rfd ->
                            var tmf = this.getTargetModelField(rfd as FieldBase)
                            var rmf = this.getRelationModelField(rfd as FieldBase)
                            // m2m field in main record
                            if(!sourceID2ModelDataArrayContainer.containsKey(sourceId)
                                    || !sourceID2ModelDataArrayContainer[sourceId]?.containsKey(rfd)!!)
                            {
                                var rs=ModelDataObject(fields = arrayListOf(),model = tmf?.first)
                                var mrfd=modelRelationMatcher.getRelationMatchField(model,relModel)
                                rs.fromIdValue=sourceId
                                rs.fromField=mrfd?.fromField
                                rs.toField=mrfd?.toField
                                fdMap[rfd]=rs
                            }

                            var mFields = arrayListOf<FieldBase>()
                            targetFields.forEach {
                                if (it.model == tmf!!.first) {
                                    mFields.add(it)
                                }
                            }

                            var subModelDataObject = ModelDataObject(fields=mFields, model = tmf?.first)
                            var mrfd=modelRelationMatcher.getRelationMatchField(relModel,tmf?.first)
                            subModelDataObject.fromField = mrfd?.fromField
                            subModelDataObject.fromIdValue = sourceId
                            subModelDataObject.toField=mrfd?.toField


                            var subRec = FieldValueArray()
                            mFields.forEach {
                                var mFV=relRecord.firstOrNull {mit->
                                    mit.field.isSame(it)
                                }
                                if(mFV!=null){
                                    subRec.add(mFV)
                                }
                            }

                            subModelDataObject.data=subRec
                            nRec.setValue(rmf!!.second!!,subModelDataObject)
                        }
                        //sourceId2ModelRecordSet[sourceId]=modelRecordSet
                        relModelDataArray.data.add(nRec)
                        //Pair(null as FieldBase, modelRecordSet)

                        fdMap

                    }
                    else{
                        var tmf=this.getTargetModelField(fd as FieldBase)
                        if(tmf?.first==null){
                            return@f
                        }
                        var mFields=arrayListOf<FieldBase>()
                        targetFields.forEach {
                            if(it.model==tmf!!.first){
                                mFields.add(it)
                            }
                        }
                        var modelDataArray=if(sourceID2ModelDataArrayContainer.containsKey(sourceId) && sourceID2ModelDataArrayContainer[sourceId]!!.containsKey(fd)){
                            sourceID2ModelDataArrayContainer[sourceId]?.get(fd)!! as ModelDataArray
                        }
                        else {
                            var modelDataArray=ModelDataArray(fields=mFields,model=tmf.first)
                            var mrfd=modelRelationMatcher.getRelationMatchField(model,tmf.first)
                            modelDataArray.fromField=mrfd?.fromField
                            modelDataArray.fromIdValue=sourceId
                            modelDataArray.toField=mrfd?.toField
                            modelDataArray
                        }
                        var nRec=FieldValueArray()
                        mFields.forEach {
                           // var key=it.getFullName()!!
                           // nRec[it.propertyName]=relRecord[key]
                            var fv=relRecord.firstOrNull {rfv->
                                rfv.field.isSame(it)
                            }
                            if(fv!=null){
                                nRec.add(fv)
                            }
                        }
                        modelDataArray.data.add(nRec)

                        mapOf(fd as FieldBase to modelDataArray)
                    }

                    var fs=sourceID2ModelDataArrayContainer[sourceId]
                    mrs.forEach { t, u ->
                        if(fs!=null){
                            fs?.put(t,u)
                        }
                        else{
                            fs=LinkedHashMap<FieldBase?,ModelData>()
                            fs?.put(t,u)
                            sourceID2ModelDataArrayContainer[sourceId]= fs!!
                        }

                    }

                }

                if(relModel!=null){
                    return@r
                }
            }
        }

        var idFd=model!!.fields!!.getIdField()!!

        reqMainArray?.data?.forEach {
            var mSrcId: Long? = it.firstOrNull { mit->
                mit.field.isSame(idFd)
            }?.value as Long? ?: return@forEach
            var mrs= sourceID2ModelDataArrayContainer[mSrcId!!]
            mrs?.forEach { f, md ->
                if(f !is ConstRelRegistriesField){
                    setOrReplaceFieldValueArrayItem(it,f!!,md)
                }
                else
                {
                    var relRegField=it.firstOrNull { rit->
                          rit.field.isSame(f)
                    }
                    if(relRegField!=null){
                        if(relRegField.value is ModelDataSharedObject){
                            (relRegField.value as ModelDataSharedObject).data[md.model]=md
                        }
                        else{
                            throw ModelErrorException("${ModelReservedKey.relRegistriesFieldKey} is reserved keyword,dont use it as field name or property name")
                        }
                    }
                    else{
                        it.add(FieldValue(f,md))
                    }
                }
            }
        }
    }
    private fun setOrReplaceFieldValueArrayItem(fVArr:FieldValueArray,field:FieldBase,value:Any?){
        var index=fVArr.indexOfFirst {
            it.field.isSame(field)
        }
        if(index>-1){
            fVArr[index]= FieldValue(field,value)
        }
        else{
            fVArr.add(FieldValue(field,value))
        }
    }
    protected open fun reconstructSingleRelationModelRecordSet(mDataArray:ModelDataArray?,
                                                               modelRelationMatcher: ModelRelationMatcher):ModelDataArray?{
        var mainModel = mDataArray?.model
        var mainModelFields=ArrayList<FieldBase>()
        var subModels= mutableMapOf<ModelBase?,ModelDataObject>()
        mDataArray?.fields?.forEach {
            if(mainModel!=it.model){
               if(subModels.contains(it.model)){
                   subModels[it.model]?.fields?.add(it)
               }
               else
               {
                   var fields=ArrayList<FieldBase>()
                   fields.add(it)
                   var mrDataObject=ModelDataObject(fields=fields,model=it.model)
                   var mfd=modelRelationMatcher.getRelationMatchField(mainModel,it.model)
                   mrDataObject.fromField=mfd?.fromField
                   mrDataObject.toField=mfd?.toField
                   subModels[it.model]=mrDataObject
               }
            }
            else{
                mainModelFields.add(it)
            }
        }
        var mainModelDataArray = ModelDataArray(fields=mainModelFields,model=mainModel)
        mDataArray?.data?.forEach { fvArr->
            var mainRecord= FieldValueArray()
            mainModelDataArray.fields?.forEach {mf->
                //var key= it.getFullName()
                //mainRecord[it.propertyName]= mit[key]
                //todo performance
                var fv=fvArr.firstOrNull {
                    it.field.isSame(mf)
                }
                if(fv!=null){
                    mainRecord.add(fv)
                }
            }
            subModels.values.forEach {
                var subRecord = FieldValueArray()
                it.fields?.forEach {fb->
                    var fv=fvArr.firstOrNull {sf->
                        sf.field.isSame(fb)
                    }
                    if(fv!=null){
                        subRecord.add(fv)
                    }
                }
                it.data=subRecord
                var mainIdFd=mainModel!!.fields!!.getIdField()
                var mainIdFV=fvArr.firstOrNull {
                    faIt->
                    faIt.field.isSame(mainIdFd!!)
                }
                it.fromIdValue=mainIdFV?.value as Long?
                mainModelDataArray.fields?.add(it.fromField!!)
                mainRecord.add(FieldValue(it.fromField!!,it))
            }
            mainModelDataArray.data.add(mainRecord)
        }
        return mainModelDataArray
    }

    protected open fun getRelationModelField(field:FieldBase):Pair<ModelBase?,FieldBase?>?{
        if((field is Many2ManyField)){
            var model=this.appModel?.getModel((field as RefRelationField).relationModelTable!!)
            var mField=model?.fields?.getField((field as RefRelationField).relationModelFieldName)
            return Pair(model,mField)
        }
        return null
    }
    protected  open fun getTargetModelField(field:FieldBase):Pair<ModelBase?,FieldBase?>?{
        if(field is RefTargetField){
            var model=this.appModel?.getModel(field.targetModelTable!!)
            var mField=model?.fields?.getField(field.targetModelFieldName)
            return Pair(model,mField)
        }
        return null
    }




    fun acCreate(modelData:ModelData,
                 partnerCache:PartnerCache):Pair<Long?,String?>{
            return this.safeCreate(modelData,useAccessControl=true,partnerCache = partnerCache)
    }

    open fun safeCreate(modelData:ModelData,
                        useAccessControl: Boolean=false,
                        partnerCache:PartnerCache?=null):Pair<Long?,String?>{
        if(useAccessControl && partnerCache==null){
            return Pair(0,"权限接口没有提供操作用户信息")
        }
        val def = DefaultTransactionDefinition()
        def.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
        val status = txManager?.getTransaction(def)
        try {
           // var dependingModelFieldValueCollection
            if(modelData.context==null){
                modelData.createContext()
            }
            var (id,errMsg)=(modelData.model as AccessControlModel)
                    .rawCreate(modelData,useAccessControl,partnerCache)
            if(id!=null && id>0){
                txManager?.commit(status)
                return Pair(id,null)
            }
        } catch (ex: Exception) {
            logger.error(ex.message)
        }
        try
        {
            //Hikaricp discard database connection when some error occur
            txManager?.rollback(status)
        }
        catch(ex:Exception)
        {
            logger.error(ex.message)
        }

        return Pair(0,"添加失败")
    }

    open fun getCreateFieldValue(field:FieldBase,value:Any?,partnerCache:PartnerCache?=null):FieldValue?{

            return when(value){
                is ModelDataObject->{
                    return if(value.idFieldValue!=null){
                         FieldValue(field,value.idFieldValue?.value)
                    }
                    else{
                         FieldValue(field,null)
                    }
                }
                is FieldDefaultValueBillboard->{
                    when(value){
                        is CurrCorpBillboard-> FieldValue(field,value.look(partnerCache))
                        is CurrPartnerBillboard->FieldValue(field,value.look(partnerCache))
                        else-> FieldValue(field,value.look(null))
                    }
                }
                else->{
                     FieldValue(field,value)
                }
            }

    }

    open fun rawCreate(data:ModelData,
                       useAccessControl: Boolean=false,
                       partnerCache:PartnerCache?=null):Pair<Long?,String?>{


        when(data){
            is ModelDataObject->{
                return rawCreateObject(data,useAccessControl,partnerCache)
            }
            is ModelDataArray->{
                return rawCreateArray(data,useAccessControl,partnerCache)
            }
        }
        return Pair(null,"not support")
    }

    // muse call in safeCreate
    protected open fun rawCreateArray(modelDataArray:ModelDataArray,
                                      useAccessControl: Boolean=false,
                                      partnerCache:PartnerCache?=null):Pair<Long?,String?>{
        for (d in modelDataArray.data){
            var obj=ModelDataObject(d,model=modelDataArray.model,fields = modelDataArray.fields)
            obj.context=modelDataArray.context
            var ret=(modelDataArray.model as AccessControlModel).rawCreateObject(obj,useAccessControl,partnerCache)
            if(ret.first==null || ret.second!=null){
                return ret
            }
        }
        return Pair(1,null)
    }
    protected  open fun beforeCreateObject(modelDataObject:ModelDataObject,
                                           useAccessControl: Boolean=false,
                                           partnerCache:PartnerCache?=null):Pair<Boolean,String?>
    {
        if(useAccessControl || partnerCache!=null){
            if(partnerCache==null){
                return Pair(false,"权限接口没有提供操作用户信息")
            }
            this.runCreateFieldsInitializeRules(modelDataObject,partnerCache)
            var ret = this.runCreateFieldsCheckRules(modelDataObject,partnerCache)
            if(!ret.first){
                return ret
            }
            this.runCreateFieldsFilterRules(modelDataObject,partnerCache)
        }
        return Pair(true,null)
    }

    protected open fun getModelCreateFieldsInspectors():Array<ModelFieldInspector>?{
        return null
    }
    protected  open fun getModelCreateFieldsInStoreInspectors():Array<ModelFieldInspector>?{
        return null
    }

    protected  open fun runCreateFieldsFilterRules(modelDataObject: ModelDataObject, partnerCache: PartnerCache){
        val model = modelDataObject.model?:this

        var modelRule = partnerCache.getCreateModelRule(model.meta.appName,model.meta.name)
        modelRule?.fieldRules?.forEach { _, u ->
            if(u.createAction.enable==1) {
                if(u.createAction.setValue!=null){
                    modelDataObject.setFieldValue(u.field,this.getValueFromPartnerContextConstKey(u.createAction.setValue,partnerCache))
                } else if(u.createAction.defalut!=null && !modelDataObject.hasFieldValue(u.field)){
                    modelDataObject.setFieldValue(u.field,this.getValueFromPartnerContextConstKey(u.createAction.defalut,partnerCache))
                }
            } else{
                modelDataObject.removeFieldValue(u.field)
            }
        }

        var filterRules = partnerCache.getModelCreateAccessControlRules<ModelCreateFieldsValueFilterRule<*>>(model)

        filterRules?.forEach {
            it(modelDataObject,partnerCache,null)
        }

    }
    protected open  fun getValueFromPartnerContextConstKey(value:String?,partnerCache:PartnerCache):String? {
        if(value.isNullOrEmpty()){
            return value
        }
        var ret= partnerCache.getContextValue(value)
        return if(ret.first){
            if(ret.second!=null){
                ret.second.toString()
            }
            else
            {
                null
            }
        }
        else{
            value
        }
    }
    protected open fun runCreateFieldsCheckRules(modelDataObject: ModelDataObject, partnerCache: PartnerCache):Pair<Boolean,String?>{
        val model = modelDataObject.model?:this
        var inspectors=this.getModelCreateFieldsInspectors()
        var ret = modelCreateFieldsInspectorCheck(modelDataObject,partnerCache,inspectors)
        if(!ret.first){
            return ret
        }

        var modelCreateFieldsChecks = partnerCache.getModelCreateAccessControlRules<ModelCreateRecordFieldsValueCheckControlRule<*>>(model)

        modelCreateFieldsChecks?.forEach {
            ret = it(modelDataObject,partnerCache,null)
            if(!ret.first){
                return ret
            }
        }


        inspectors = this.getModelCreateFieldsInStoreInspectors()
        ret =partnerModelCreateFieldsInStoreInspectorCheck(modelDataObject,partnerCache,inspectors)
        if(!ret.first){
            return ret
        }

        var modelCreateFieldsInStoreChecks = partnerCache.getModelCreateAccessControlRules<ModelCreateRecordFieldsValueCheckInStoreControlRule<*>>(model)
        modelCreateFieldsInStoreChecks?.forEach {
            ret = it(modelDataObject,partnerCache,null)
            if(!ret.first){
                return ret
            }
        }

        return Pair(true,null)
    }

    protected open fun runCreateFieldsInitializeRules(modelDataObject: ModelDataObject, partnerCache: PartnerCache){
        val model = modelDataObject.model?:this
        this.createRecordSetIsolationFields(modelDataObject,partnerCache)

        var rules = partnerCache.getModelCreateAccessControlRules<ModelCreateRecordFieldsInitializeRule<*>>(model)
        rules?.forEach {
            it(modelDataObject,partnerCache,null)
        }
    }
    protected  open fun rawCreateObject(modelDataObject:ModelDataObject,
                                        useAccessControl: Boolean=false,
                                        partnerCache:PartnerCache?=null):Pair<Long?,String?>{

        var constGetRefField=modelDataObject.data.firstOrNull {
            it.field is ConstGetRecordRefField
        }

        if(constGetRefField!=null){
            return if(modelDataObject.context?.refRecordMap?.containsKey(constGetRefField.value as String)!!){
                var fvc=modelDataObject.context?.refRecordMap?.get(constGetRefField.value as String) as ModelDataObject
                var idValue=fvc?.idFieldValue?.value as Long?
                if(idValue!=null) {
                    modelDataObject.data.add(FieldValue(modelDataObject.model?.fields?.getIdField()!!,idValue))
                    Pair(idValue,null)
                } else{
                    Pair(null,"cant find the ref record")
                }

            } else{
                Pair(null,"cant find the ref record")
            }
        }

        var ret= this.beforeCreateObject(modelDataObject,useAccessControl,partnerCache)
        if(!ret.first){
            Pair(null,ret.second)
        }

//        var (result,errorMsg)=this.beforeCreateCheck(modelDataObject,useAccessControl = useAccessControl,partnerCache = partnerCache)
//        if(!result){
//            return Pair(null,errorMsg)
//        }

        modelDataObject.data.forEach {
            when(it.field){
                is Many2OneField,is One2OneField->{
                    if(it.field is One2OneField && it.field.isVirtualField){
                        return@forEach
                    }
                    if(it.value is ModelDataObject){
                        if(it.value.idFieldValue==null){
                            it.value.context=modelDataObject.context
                            var id=(it.value.model as AccessControlModel?)?.rawCreate(it.value,useAccessControl,partnerCache)
                            if(id==null ||id.second!=null){
                                return id?:Pair(null,"创建失败")
                            }
                            var idField=it.value?.model?.fields?.getIdField()
                            it.value.data.add(FieldValue(idField!!,id?.first))
                        }
                    }
                }
            }
        }

//        if(useAccessControl)
//        {
//            var rules=partnerCache?.acGetCreateRules(modelDataObject.model)
//            rules?.forEach {
//                var (ok,errorMsg)=it.check(modelDataObject,context = partnerCache?.modelExpressionContext!!)
//                if(!ok){
//                    return Pair(null,errorMsg)
//                }
//            }
//        }

        return try {
            var fVCShadow=ModelDataObject(model=modelDataObject.model)
            modelDataObject.model?.fields?.getAllFields()?.values?.forEach {
                if((it is ModelFunctionField) || (it is ModelOne2ManyField)
                        ||(it is ModelMany2ManyField)){
                    return@forEach
                }
                val oit = it as ModelField
                var fv = modelDataObject.data.firstOrNull { fv->
                    fv.field.getFullName() == oit.getFullName()
                }
                if (fv == null) {
                    if (oit.defaultValue != null) {
                        var acFV=this.getCreateFieldValue(oit, oit.defaultValue)
                        if(acFV!=null){
                            fVCShadow.data.add(acFV)
                        }
                    }
                }
            }
            var nID=this.create(fVCShadow)
            if(nID==null || nID<1){
                return Pair(null,"创建失败")
            }
            modelDataObject.data.add(FieldValue(
                    modelDataObject.model?.fields?.getIdField()!!,
                    nID
            ))
            var refField=modelDataObject.data.firstOrNull{
                it.field is ConstSetRecordRefField
            }
            if(refField!=null){
                modelDataObject.context?.refRecordMap?.set(refField.value as String,modelDataObject)
            }
            modelDataObject.data.forEach {
                fv->
                when(fv.field){
                    is One2ManyField->{
                        if(fv.value is ModelDataObject && fv.value.idFieldValue==null){
                            var tmf=this.getTargetModelField(fv.field)
                            fv.value.data.add(FieldValue(tmf?.second!!,nID))
                            fv.value.context=modelDataObject.context
                            var o2m=(tmf?.first as AccessControlModel?)?.rawCreate(fv.value,useAccessControl,partnerCache)
                            if (o2m==null || o2m.second!=null){
                                return  Pair(null,"创建失败")
                            }
                        }
                        else if(fv.value is ModelDataArray){
                            var tmf=this.getTargetModelField(fv.field)
                            fv.value.context=modelDataObject.context
                            fv.value.data.forEach {
                                    it.add(FieldValue(tmf?.second!!,nID))
                            }
                            var ret=this.rawCreateArray(fv.value,useAccessControl,partnerCache)
                            if(ret.first==null ||ret.second!=null){
                                return ret
                            }
                        }
                    }
                    is One2OneField->{
                        if(fv.field.isVirtualField){
                            if(fv.value is ModelDataObject && fv.value.idFieldValue==null){
                                var tmf=this.getTargetModelField(fv.field)
                                fv.value.data.add(FieldValue(tmf?.second!!,nID))
                                fv.value.context=modelDataObject.context
                                var o2o=(tmf?.first as AccessControlModel?)?.rawCreate(fv.value,useAccessControl,partnerCache)
                                if (o2o==null || o2o.second!=null){
                                    return  Pair(null,"创建失败")
                                }
                            }
                        }
                    }
                    is ConstRelRegistriesField->{
                        when(fv.value){
                            is ModelDataSharedObject->{
                                for( kv in fv.value.data) {
                                    when(kv.value){
                                        is ModelDataObject->{
                                            var mfvc= kv.value as ModelDataObject
                                            mfvc.context=modelDataObject.context
                                            var tField=mfvc.model?.fields?.getFieldByTargetField(modelDataObject.model?.fields?.getIdField())
                                            if(tField!=null && mfvc.idFieldValue==null){
                                                mfvc.data.add(FieldValue(tField,nID))
                                                var ret=(mfvc.model as AccessControlModel?)?.rawCreate(mfvc,useAccessControl,partnerCache)
                                                if(ret==null || ret.second!=null){
                                                    return Pair(null,"创建失败")
                                                }
                                            }
                                        }
                                        is ModelDataArray->{
                                            var mmfvc= kv.value as ModelDataArray
                                            mmfvc.context=modelDataObject.context
                                            for(mkv in mmfvc.data){
                                                var tField=mmfvc.model?.fields?.getFieldByTargetField(modelDataObject.model?.fields?.getIdField())
                                                if(tField!=null){
                                                    mkv.add(FieldValue(tField,nID))
                                                }
                                            }
                                            var ret=rawCreateArray(mmfvc,useAccessControl,partnerCache)
                                            if(ret.first==null ||ret.second!=null){
                                                return ret
                                            }
                                        }
                                        else->{

                                        }
                                    }
                                }
                            }
                        }
                        // return Pair(null,null)
                    }
                }
            }
            return Pair(nID,null)
        } catch (ex:Exception){
            return Pair(null,ex.message)
        }
    }
//    protected open  fun beforeCreateCheck(modelData:ModelDataObject,
//                                          useAccessControl: Boolean,
//                                          partnerCache:PartnerCache?):Pair<Boolean,String?>{
//
//        return Pair(true,null)
//    }

    protected  open fun beforeEditCheck(filedValueCollection:ModelData,
                                     useAccessControl: Boolean,
                                     partnerCache:PartnerCache?):Pair<Boolean,String?>{
        return Pair(true,null)
    }



    open fun acEdit(modelData:ModelData,
                    criteria:ModelExpression?,
                    partnerCache:PartnerCache):Pair<Long?,String?>{
        return this.safeEdit(modelData,
                criteria = criteria,
                useAccessControl = true,
                partnerCache = partnerCache)
    }



    open fun safeEdit(modelData: ModelData,
                      criteria:ModelExpression?=null,
                      useAccessControl: Boolean=false,
                      partnerCache:PartnerCache?=null):Pair<Long?,String?>{


        if(useAccessControl && partnerCache==null){
            return Pair(0,"权限接口没有提供操作用户信息")
        }
        val def = DefaultTransactionDefinition()
        def.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
        val status = txManager?.getTransaction(def)
        try {
            // var dependingModelFieldValueCollection
            if(modelData.isObject()){
                var ret= this.rawEdit(modelData.`as`(),
                        criteria=criteria,
                        useAccessControl=useAccessControl,
                        partnerCache = partnerCache)
                txManager?.commit(status)
                return  ret
            }
            else{

            }

        } catch (ex: Exception) {
            logger.error(ex.message)
        }
        try {
            txManager?.rollback(status)
        }
        catch (ex:Exception)
        {
            logger.error(ex.message)
        }
        return Pair(0,"更新失败")
    }
    //todo add model role constraint
    open fun getACEditFieldValue(field:FieldBase,value:Any?,useAccessControl: Boolean,partnerCache: PartnerCache?,useDefault:Boolean=false):FieldValue?{
        return FieldValue(field,value)
    }

    open fun rawEdit(modelDataObject: ModelDataObject,
                     criteria:ModelExpression?,
                     useAccessControl: Boolean=false,
                     partnerCache:PartnerCache?=null):Pair<Long?,String?>{


        var (result,errorMsg)=this.beforeEditCheck(modelDataObject,useAccessControl = useAccessControl,partnerCache = partnerCache)
        if(!result){
            return Pair(null,errorMsg)
        }

//        if(useAccessControl)
//        {
//            var rules=partnerCache?.acGetEditRules(modelDataObject.model)
//            rules?.forEach {
//                var (ok,errorMsg)=it.check(modelDataObject,context=partnerCache?.modelExpressionContext!!)
//                if(!ok){
//                    return Pair(null,errorMsg)
//                }
//            }
//           // return  return Pair(null,errorMsg)
//        }

        return try {

            var tCriteria=criteria
            var idFV=modelDataObject.idFieldValue
            if(idFV!=null){
                var idCriteria=eq(idFV.field,idFV.value)
                tCriteria= if(tCriteria!=null) {
                    and(tCriteria,idCriteria!!)!!
                } else idCriteria
            }

            if(useAccessControl)
            {
                var acCriteria=null as ModelExpression?//partnerCache?.acGetEditCriteria(modelDataObject.model)
                if(acCriteria!=null){
                    tCriteria= if(tCriteria!=null) {
                        and(tCriteria,acCriteria)!!
                    } else acCriteria
                }
            }

            modelDataObject.data.forEach {
                when(it.field){

                    is Many2OneField,is One2OneField->{

                        if(it.field is One2OneField && it.field.isVirtualField){
                            return@forEach
                        }

                        if(it.value is ModelDataObject){
                            if(it.value.idFieldValue==null){
                                it.value.context=modelDataObject.context
                                var id=(it.value.model as AccessControlModel?)?.rawCreate(it.value,useAccessControl,partnerCache)
                                if(id==null ||id.second!=null){
                                    return id?:Pair(null,"创建失败")
                                }
                                it.value.data.add(FieldValue(it.value?.model?.fields?.getIdField()!!,id?.first))
                            }
                            else{
                                it.value.context=modelDataObject.context
                                var ret=(it.field.model as AccessControlModel?)?.rawEdit(it.value,null,useAccessControl,partnerCache)
                                if(ret==null ||ret.second!=null){
                                    return ret?:Pair(null,"更新失败")
                                }
                            }
                        }
                    }
                }
            }


            var fVCShadow=ModelDataObject(model=modelDataObject.model,fields = modelDataObject.fields)
            modelDataObject.model?.fields?.getAllFields()?.values?.forEach {

                if((it is ModelFunctionField) || (it is ModelOne2ManyField)
                        ||(it is ModelMany2ManyField)){
                    return@forEach
                }
                val oit = it as ModelField
                if(oit.isIdField()){
                    return@forEach
                }
                var fv = modelDataObject.data.firstOrNull { fv->
                    fv.field.getFullName() == oit.getFullName()
                }
                if (fv != null) {
                    var acFV=this.getACEditFieldValue(fv.field, fv.value,useAccessControl,partnerCache)
                    if(acFV!=null){
                        fVCShadow.data.add(acFV)
                    }
                }
            }

            var ret=this.update(fVCShadow,criteria = tCriteria)
            if(ret==null ||ret<1){
               return  Pair(null,"更新失败")
            }
            var mIDFV=modelDataObject.idFieldValue
            if(mIDFV!=null){
                modelDataObject.data.forEach {
                    fv->
                    when(fv.field){
                        is One2ManyField->{
                            if(fv.value is ModelDataObject){
                                var tmf=this.getTargetModelField(fv.field)
                                if(fv.value.idFieldValue==null)
                                {
                                    fv.value.context=modelDataObject.context
                                    fv.value.data.add(FieldValue(tmf?.second!!,mIDFV.value))
                                    var o2m=(tmf?.first as AccessControlModel?)?.rawCreate(fv.value,useAccessControl,partnerCache)
                                    if (o2m==null || o2m.second!=null){
                                        return  Pair(null,"创建失败")
                                    }
                                }
                                else{
                                    fv.value.context=modelDataObject.context
                                    var ret=(tmf?.first as AccessControlModel?)?.rawEdit(fv.value,null,useAccessControl,partnerCache)
                                    if (ret==null || ret.second!=null){
                                        return  Pair(null,"更新失败")
                                    }
                                }
                            }
                            else if(fv.value is ModelDataArray){
                                var tmf=this.getTargetModelField(fv.field)
                                fv.value.data.forEach {
                                    var tfvc=ModelDataObject(it,fv.value.model)
                                    if(tfvc.idFieldValue==null){
                                        tfvc.context=modelDataObject.context
                                        tfvc.data.add(FieldValue(tmf?.second!!,mIDFV.value))
                                        var o2m=(tmf?.first as AccessControlModel?)?.rawCreate(tfvc,useAccessControl,partnerCache)
                                        if (o2m==null || o2m.second!=null){
                                            return  Pair(null,"创建失败")
                                        }
                                    }
                                    else
                                    {
                                        tfvc.context=modelDataObject.context
                                        var ret=(tmf?.first as AccessControlModel?)?.rawEdit(tfvc,null,useAccessControl,partnerCache)
                                        if (ret==null || ret.second!=null){
                                            return  Pair(null,"更新失败")
                                        }
                                    }
                                }
                            }
                        }
                        is ConstRelRegistriesField->{
                            when(fv.value){
                                is ModelDataSharedObject->{
                                    for( kv in fv.value.data) {
                                        when(kv.value){
                                            is ModelDataObject->{
                                                var mfvc= kv.value as ModelDataObject
                                                mfvc.context=modelDataObject.context
                                                var tField=mfvc.model?.fields?.getFieldByTargetField(modelDataObject.model?.fields?.getIdField())
                                                if(tField!=null && mfvc.idFieldValue==null){
                                                    mfvc.context=modelDataObject.context
                                                    mfvc.data.add(FieldValue(tField,mIDFV.value))
                                                    var ret=(mfvc.model as AccessControlModel?)?.rawCreate(mfvc,useAccessControl,partnerCache)
                                                    if(ret==null || ret.second!=null){
                                                        return Pair(null,"创建失败")
                                                    }
                                                }
                                                else if(mfvc.idFieldValue!=null){
                                                    mfvc.context=modelDataObject.context
                                                    var ret=(mfvc.model as AccessControlModel?)?.rawEdit(mfvc,null,useAccessControl,partnerCache)
                                                    if(ret==null || ret.second!=null){
                                                        return Pair(null,"更新失败")
                                                    }
                                                }
                                            }
                                            is ModelDataArray->{
                                                var mmfvc= kv.value as ModelDataArray
                                                for(mkv in mmfvc.data){
                                                    var mfvc=ModelDataObject(mkv,mmfvc.model)
                                                    mfvc.context=modelDataObject.context
                                                    var tField=mfvc.model?.fields?.getFieldByTargetField(modelDataObject.model?.fields?.getIdField())
                                                    if(tField!=null && mfvc.idFieldValue==null){
                                                        mfvc.data.add(FieldValue(tField,mIDFV.value))
                                                        var ret=(mfvc.model as AccessControlModel?)?.rawCreate(mfvc,useAccessControl,partnerCache)
                                                        if(ret==null || ret.second!=null){
                                                            return Pair(null,"创建失败")
                                                        }
                                                    }
                                                    else if(mfvc.idFieldValue!=null){
                                                        mfvc.context=modelDataObject.context
                                                        var ret=(mfvc.model as AccessControlModel?)?.rawEdit(mfvc,null,useAccessControl,partnerCache)
                                                        if(ret==null || ret.second!=null){
                                                            return Pair(null,"更新失败")
                                                        }
                                                    }
                                                }
                                            }
                                            else->{

                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Pair(ret,null)
        } catch (ex:Exception){
            Pair(null,ex.message)
        }
    }

    protected  open fun beforeDeleteCheck(modelData: ModelData,
                                       criteria:ModelExpression?,
                                       useAccessControl: Boolean,
                                       partnerCache:PartnerCache?):Pair<Boolean,String?>{
        return Pair(true,null)
    }

    open fun acDelete(modelData: ModelData,
                      criteria:ModelExpression?,
                      useAccessControl:Boolean,
                      partnerCache:PartnerCache?):Pair<Long?,String?>{

        return this.safeDelete(modelData,
                criteria = criteria,
                useAccessControl = useAccessControl,
                partnerCache = partnerCache)
    }


    open fun safeDelete(modelData: ModelData,
                        criteria:ModelExpression?,
                        useAccessControl: Boolean=false,
                        partnerCache:PartnerCache?=null):Pair<Long?,String?>{

        if(useAccessControl && partnerCache==null){
            return Pair(0,"权限接口没有提供操作用户信息")
        }
        val def = DefaultTransactionDefinition()
        def.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
        val status = txManager?.getTransaction(def)
        try {
            // var dependingModelFieldValueCollection
            if(modelData.isObject()){
                var ret= this.rawDelete(modelData.`as`(),
                        criteria=criteria,
                        useAccessControl=useAccessControl,
                        partnerCache = partnerCache)
                txManager?.commit(status)
                return  ret
            }

        } catch (ex: Exception) {
            logger.error(ex.message)
        }
        try {
            txManager?.rollback(status)
        }
        catch (ex:Exception)
        {
            logger.error(ex.message)
        }
        return Pair(0,"更新失败")
    }


    open fun rawDelete(modelDataObject: ModelDataObject,
                       criteria:ModelExpression?,
                       useAccessControl: Boolean=false,
                       partnerCache:PartnerCache?=null):Pair<Long?,String?>{
        if(useAccessControl && partnerCache==null){
            return Pair(null,"must login")
        }
        var (result,errorMsg)=this.beforeDeleteCheck(modelDataObject,criteria = criteria,useAccessControl = useAccessControl,partnerCache = partnerCache)
        if(!result){
            return Pair(null,errorMsg)
        }

//        if(useAccessControl){
//            var rules=partnerCache?.acGetDeleteRules(modelDataObject.model)
//            rules?.forEach {
//                var (ok,errorMsg)=it.check(modelDataObject,context = partnerCache?.modelExpressionContext!!)
//                if(!ok){
//                    return Pair(null,errorMsg)
//                }
//            }
//        }


        return try {
            var tCriteria=criteria
            var idFV=modelDataObject.idFieldValue
            if(idFV!=null){
                var idCriteria=eq(idFV.field,idFV.value)
                tCriteria= if(tCriteria!=null) {
                    and(tCriteria,idCriteria!!)!!
                } else idCriteria
            }

            if(useAccessControl)
            {
                var acCriteria=null as ModelExpression?//partnerCache?.acGetEditCriteria(modelDataObject.model)
                if(acCriteria!=null){
                    tCriteria= if(tCriteria!=null) {
                        and(tCriteria,acCriteria)!!
                    } else acCriteria
                }
            }

            Pair(this.delete(modelDataObject,criteria = tCriteria),null)
        } catch (ex:Exception){
            Pair(null,ex.message)
        }
    }

    open fun rawCount(fieldValueArray:FieldValueArray):Int{
        var expArr = fieldValueArray.map {
            eq(it.field,it.value)!!
        }.toTypedArray()
        var expressions = and(*expArr)
        var statement = select(fromModel = this).count().where(expressions)
        return this.queryCount(statement)
    }
}