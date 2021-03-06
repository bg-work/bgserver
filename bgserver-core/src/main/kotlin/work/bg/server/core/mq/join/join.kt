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

package work.bg.server.core.mq.join

import work.bg.server.core.mq.ModelBase
import work.bg.server.core.mq.ModelCriteria
import work.bg.server.core.mq.ModelExpression
import work.bg.server.core.mq.OrderBy

private class  JoinTag{
    companion object {
        const val INNER_JOIN=" INNER JOIN "
        const val LEFT_JOIN=" LEFT JOIN  "
        const val RIGHT_JOIN=" RIGHT JOIN "
        const val LATERAL_INNER_JOIN=" LATERAL INNER JOIN "
        const val LATERAL_LEFT_JOIN=" LATERAL INNER JOIN "
    }
}
fun innerJoin(model: ModelBase?, onConditions: ModelExpression,criteria:ModelExpression?=null)=JoinModel(model,onConditions,JoinTag.INNER_JOIN,criteria)
fun leftJoin(model: ModelBase?, onConditions: ModelExpression,criteria:ModelExpression?=null)=JoinModel(model,onConditions,JoinTag.LEFT_JOIN,criteria)
fun rightJoin(model: ModelBase?, onConditions: ModelExpression,criteria:ModelExpression?=null)=JoinModel(model,onConditions,JoinTag.RIGHT_JOIN,criteria)

fun lateralInnerJoin(model: ModelBase?,onCondition: ModelExpression,criteria: ModelExpression?=null,fetchCount:Int?=null,orderBy: OrderBy?=null)=
        LateralJoinModel(model,JoinTag.LATERAL_INNER_JOIN,onCondition,criteria,fetchCount,orderBy )
fun lateralLeftJoin(model: ModelBase?,onCondition: ModelExpression,criteria: ModelExpression?=null,fetchCount:Int?=null,orderBy: OrderBy?=null)=
        LateralJoinModel(model,JoinTag.LATERAL_LEFT_JOIN,onCondition,criteria,fetchCount,orderBy )