<template>
  <div>
    <!-- ============ 集团合并口径 ============ -->
    <div class="page-card">
      <h3 class="page-title">集团合并口径（运营控制责任归属）</h3>
      <div class="toolbar">
        <el-input v-model="period" style="width:130px" placeholder="期间 YYYY-MM" />
        <el-button type="primary" @click="loadConsolidated">查询</el-button>
        <span class="hint">纳入=集团承担运营控制；排除=集团外主体承担；待裁定=控制权与设备交接不一致，未决前不并入任一集团趋势</span>
      </div>

      <template v-if="consolidated">
        <el-row :gutter="12" style="margin-bottom:12px">
          <el-col :span="6">
            <el-statistic title="纳入合并 (tCO2e)" :value="toNum(consolidated.totals.includedTco2e)" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="排除 (tCO2e)" :value="toNum(consolidated.totals.excludedTco2e)" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="待裁定 (tCO2e)" :value="toNum(consolidated.totals.pendingTco2e)" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="待裁定片段数" :value="Number(consolidated.totals.pendingSegmentCount)" />
          </el-col>
        </el-row>
        <div class="hint" style="margin-bottom:8px">
          运行 {{ consolidated.runNo || '-' }}；责任推导截止点 {{ fmtTime(consolidated.cutoff) }}
          <el-tag v-if="consolidated.hasPendingOwnership" type="danger" size="small" style="margin-left:8px">含待裁定</el-tag>
        </div>
        <el-table :data="consolidated.plants" size="small" border>
          <el-table-column label="工厂" min-width="140">
            <template #default="{ row }">{{ row.plantName }}</template>
          </el-table-column>
          <el-table-column label="纳入 (tCO2e)" width="150">
            <template #default="{ row }">{{ fmtNum(row.includedTco2e, 2) }}</template>
          </el-table-column>
          <el-table-column label="排除 (tCO2e)" width="150">
            <template #default="{ row }">{{ fmtNum(row.excludedTco2e, 2) }}</template>
          </el-table-column>
          <el-table-column label="待裁定 (tCO2e)" width="150">
            <template #default="{ row }">
              <span :class="{ 'diff-pos': toNum(row.pendingTco2e) > 0 }">{{ fmtNum(row.pendingTco2e, 2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="待裁定片段" width="100">
            <template #default="{ row }">{{ row.pendingSegmentCount }}</template>
          </el-table-column>
        </el-table>

        <h4 style="margin-top:14px">可比性注释（解释未决几天为何没有并入任一集团趋势）</h4>
        <el-table :data="consolidated.notes" size="small" border>
          <el-table-column label="类型" width="120">
            <template #default="{ row }"><StatusTag kind="noteType" :value="row.noteType" /></template>
          </el-table-column>
          <el-table-column label="工厂" width="110">
            <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
          </el-table-column>
          <el-table-column label="区间" width="200">
            <template #default="{ row }">{{ row.segmentStart || '-' }} ~ {{ row.segmentEnd || '' }}</template>
          </el-table-column>
          <el-table-column prop="content" label="注释内容" min-width="320" show-overflow-tooltip />
        </el-table>
      </template>
    </div>

    <el-tabs class="page-card" v-model="activeTab">
      <!-- ============ 不可变事件 ============ -->
      <el-tab-pane label="责任事件（不可变）" name="events">
        <div class="toolbar">
          <el-select v-model="eventFilterPlant" placeholder="全部工厂" clearable style="width:160px" @change="loadEvents">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button @click="loadEvents">刷新</el-button>
          <el-button type="primary" @click="eventDlg.visible = true">登记事件</el-button>
          <span class="hint">事件登记后不可修改/删除；追溯更正请登记 CORRECTION 事件并指向被更正事件</span>
        </div>
        <el-table :data="events" size="small" border>
          <el-table-column prop="eventNo" label="事件号" width="130" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }"><StatusTag kind="ownershipEvent" :value="row.eventType" /></template>
          </el-table-column>
          <el-table-column label="工厂" width="100">
            <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
          </el-table-column>
          <el-table-column prop="equipmentCode" label="设备" width="110">
            <template #default="{ row }">{{ row.equipmentCode || '工厂级' }}</template>
          </el-table-column>
          <el-table-column label="证据" width="110">
            <template #default="{ row }"><StatusTag kind="evidence" :value="row.evidenceType" /></template>
          </el-table-column>
          <el-table-column prop="evidenceNo" label="证据文号" width="160" show-overflow-tooltip />
          <el-table-column prop="effectiveDate" label="生效日" width="110" />
          <el-table-column prop="counterparty" label="对手方" width="140" show-overflow-tooltip />
          <el-table-column label="冲突/更正" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.conflictWith" type="danger" size="small">冲突#{{ row.conflictWith }}</el-tag>
              <el-tag v-if="row.supersedesEventId" type="warning" size="small" style="margin-left:4px">更正#{{ row.supersedesEventId }}</el-tag>
              <span v-if="!row.conflictWith && !row.supersedesEventId">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="登记人" width="90" />
          <el-table-column prop="note" label="说明" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <!-- ============ 裁定 ============ -->
      <el-tab-pane label="责任裁定" name="rulings">
        <div class="toolbar">
          <el-select v-model="rulingFilterPlant" placeholder="全部工厂" clearable style="width:160px" @change="loadRulings">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-button @click="loadRulings">刷新</el-button>
          <el-button type="primary" @click="rulingDlg.visible = true">登记裁定</el-button>
          <span class="hint">控制权与设备交接不一致的时段只能待裁定；相反法律证据不能被多数票覆盖</span>
        </div>
        <el-table :data="rulings" size="small" border>
          <el-table-column prop="rulingNo" label="裁定号" width="130" />
          <el-table-column label="工厂" width="110">
            <template #default="{ row }">{{ store.plantName(row.plantId) }}</template>
          </el-table-column>
          <el-table-column prop="equipmentCode" label="设备" width="110">
            <template #default="{ row }">{{ row.equipmentCode || '工厂级' }}</template>
          </el-table-column>
          <el-table-column label="裁定区间" width="200">
            <template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template>
          </el-table-column>
          <el-table-column label="结论" width="110">
            <template #default="{ row }"><StatusTag kind="rulingDecision" :value="row.decision" /></template>
          </el-table-column>
          <el-table-column prop="evidenceNo" label="依据文号" width="160" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag kind="ruling" :value="row.status" /></template>
          </el-table-column>
          <el-table-column prop="reason" label="理由" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <!-- ============ 片段预览 ============ -->
      <el-tab-pane label="责任片段预览（截止点推导）" name="segments">
        <div class="toolbar">
          <el-select v-model="seg.plantId" placeholder="工厂" style="width:150px">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-input v-model="seg.period" style="width:130px" placeholder="期间 YYYY-MM" />
          <el-input v-model="seg.equipmentCode" style="width:150px" placeholder="设备编号（可空）" />
          <el-button type="primary" @click="loadSegments">推导片段</el-button>
          <span class="hint">按当前时刻复演不可变事件流；历史报告按冻结时截止点复演，口径不受后续事件影响</span>
        </div>
        <el-table :data="segments" size="small" border>
          <el-table-column label="片段区间" width="220">
            <template #default="{ row }">{{ row.start }} ~ {{ row.end }}</template>
          </el-table-column>
          <el-table-column label="归属" width="120">
            <template #default="{ row }"><StatusTag kind="allocation" :value="row.classification" /></template>
          </el-table-column>
          <el-table-column prop="basis" label="推导依据" min-width="360" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ 事件登记对话框 ============ -->
    <el-dialog v-model="eventDlg.visible" title="登记不可变责任事件" width="640px" destroy-on-close>
      <el-form :model="eventDlg" label-width="110px">
        <el-form-item label="事件类型" required>
          <el-select v-model="eventDlg.eventType" style="width:100%">
            <el-option v-for="t in eventTypes" :key="t.v" :label="t.l" :value="t.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="工厂" required>
          <el-select v-model="eventDlg.plantId" style="width:100%">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编号">
          <el-input v-model="eventDlg.equipmentCode" placeholder="共享/租赁设备编号；工厂级事件留空" />
        </el-form-item>
        <el-form-item label="证据类别" required>
          <el-select v-model="eventDlg.evidenceType" style="width:100%">
            <el-option v-for="t in evidenceTypes" :key="t.v" :label="t.l" :value="t.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="证据文号" required>
          <el-input v-model="eventDlg.evidenceNo" placeholder="股权交割书/产权证/租赁合同/交接单/停计量通知文号" />
        </el-form-item>
        <el-form-item label="生效日期" required>
          <el-date-picker v-model="eventDlg.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="对手方">
          <el-input v-model="eventDlg.counterparty" placeholder="原股东/出租方/交易对手" />
        </el-form-item>
        <el-form-item v-if="eventDlg.eventType === 'LEASE'" label="租赁控制方">
          <el-radio-group v-model="eventDlg.control">
            <el-radio value="GROUP">集团运营控制</el-radio>
            <el-radio value="OTHER">出租方运营控制</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标责任主体">
          <el-select v-model="eventDlg.targetPartyId" clearable style="width:100%">
            <el-option v-for="p in parties" :key="p.id" :label="`${p.name}（${p.partyType}）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="相反法律证据">
          <el-select v-model="eventDlg.conflictEventId" clearable filterable style="width:100%">
            <el-option v-for="e in events" :key="e.id" :label="`${e.eventNo} ${e.evidenceNo}`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="eventDlg.eventType === 'CORRECTION'" label="被更正事件" required>
          <el-select v-model="eventDlg.supersedesEventId" filterable style="width:100%">
            <el-option v-for="e in events" :key="e.id" :label="`${e.eventNo} ${e.evidenceNo}`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="eventDlg.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="eventDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="submitEvent">登记</el-button>
      </template>
    </el-dialog>

    <!-- ============ 裁定登记对话框 ============ -->
    <el-dialog v-model="rulingDlg.visible" title="登记责任裁定" width="560px" destroy-on-close>
      <el-form :model="rulingDlg" label-width="100px">
        <el-form-item label="工厂" required>
          <el-select v-model="rulingDlg.plantId" style="width:100%">
            <el-option v-for="p in store.plants" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备编号">
          <el-input v-model="rulingDlg.equipmentCode" placeholder="可空" />
        </el-form-item>
        <el-form-item label="区间起" required>
          <el-date-picker v-model="rulingDlg.periodStart" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="区间止" required>
          <el-date-picker v-model="rulingDlg.periodEnd" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结论" required>
          <el-radio-group v-model="rulingDlg.decision">
            <el-radio value="INCLUDE">纳入集团</el-radio>
            <el-radio value="EXCLUDE">排除集团</el-radio>
            <el-radio value="PENDING">维持待裁定</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="依据文号" required>
          <el-input v-model="rulingDlg.evidenceNo" />
        </el-form-item>
        <el-form-item label="理由">
          <el-input v-model="rulingDlg.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rulingDlg.visible = false">取消</el-button>
        <el-button type="primary" @click="submitRuling">登记裁定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { store, fmtNum, fmtTime } from '../store'
import StatusTag from '../components/StatusTag.vue'

const period = ref('2025-07')
const activeTab = ref('events')
const consolidated = ref(null)
const events = ref([])
const rulings = ref([])
const parties = ref([])
const segments = ref([])
const eventFilterPlant = ref(null)
const rulingFilterPlant = ref(null)
const seg = reactive({ plantId: null, period: '2025-07', equipmentCode: 'EQ-WH-01' })

const eventTypes = [
  { v: 'ACQUISITION', l: '股权收购（取得法人控制权）' },
  { v: 'DIVESTITURE', l: '股权剥离（丧失法人控制权）' },
  { v: 'HANDOVER', l: '设备实际交接' },
  { v: 'METER_STOP', l: '出租方停止计量' },
  { v: 'LEASE', l: '租赁约定' },
  { v: 'CORRECTION', l: '追溯更正' }
]
const evidenceTypes = [
  { v: 'LEGAL', l: '法人控制权证据' }, { v: 'ASSET', l: '资产归属' },
  { v: 'LEASE', l: '租赁约定' }, { v: 'HANDOVER', l: '设备交接凭证' },
  { v: 'METERING', l: '停止计量通知' }, { v: 'CORRECTION', l: '更正证据' }
]

const eventDlg = reactive({
  visible: false, eventType: 'ACQUISITION', plantId: null, equipmentCode: '',
  evidenceType: 'LEGAL', evidenceNo: '', effectiveDate: '', counterparty: '',
  targetPartyId: null, conflictEventId: null, supersedesEventId: null,
  control: 'GROUP', note: ''
})
const rulingDlg = reactive({
  visible: false, plantId: null, equipmentCode: '', periodStart: '', periodEnd: '',
  decision: 'INCLUDE', evidenceNo: '', reason: ''
})

function toNum(v) { return v == null ? 0 : Number(v) }

async function loadConsolidated() {
  const resp = await api.get('/ownership/consolidated', { params: { period: period.value } })
  consolidated.value = resp.data
}
async function loadEvents() {
  const resp = await api.get('/ownership/events', { params: { plantId: eventFilterPlant.value || undefined } })
  events.value = resp.data
}
async function loadRulings() {
  const resp = await api.get('/ownership/rulings', { params: { plantId: rulingFilterPlant.value || undefined } })
  rulings.value = resp.data
}
async function loadSegments() {
  if (!seg.plantId || !seg.period) { ElMessage.warning('请选择工厂和期间'); return }
  const resp = await api.get('/ownership/segments', {
    params: { plantId: seg.plantId, period: seg.period, equipmentCode: seg.equipmentCode || undefined }
  })
  segments.value = resp.data.segments
}
async function submitEvent() {
  const body = { ...eventDlg, visible: undefined }
  await api.post('/ownership/events', body)
  ElMessage.success('事件已登记（不可变）')
  eventDlg.visible = false
  loadEvents()
}
async function submitRuling() {
  await api.post('/ownership/rulings', { ...rulingDlg, visible: undefined })
  ElMessage.success('裁定已登记')
  rulingDlg.visible = false
  loadRulings()
}

onMounted(async () => {
  await store.loadMeta()
  const p = await api.get('/ownership/parties')
  parties.value = p.data
  await Promise.all([loadConsolidated(), loadEvents(), loadRulings()])
})
</script>

<style scoped>
.hint { font-size: 12px; color: #909399; }
h4 { margin: 16px 0 8px; }
</style>
