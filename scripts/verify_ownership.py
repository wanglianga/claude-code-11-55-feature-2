#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
责任归属（运营控制边界）专项验收回放：
  S6 年中收购 + 共享租赁设备：股权交割日/停计量日/设备交接日不同 →
     责任片段切分、待裁定缺口、集团汇总（纳入/排除/待裁定）、待裁定阻断审批发布
  S7 责任裁定：待裁定区间经裁定纳入 → 自动影响评估/候选运行 → 冻结后比较版本，原范围保留
  S8 冲突证据 + 追溯更正：相反法律证据不能被多数票覆盖；CORRECTION 后冲突解除；
     原事件保留（RULED）；历史运行按各自截止点复演，不按当前股权重挂
  另含角色权限校验。
前置：后端已启动且数据库为初始种子状态（可先跑 verify.py）。
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8080'
API = BASE + '/api'
PASSED, FAILED = [], []


def req(method, path, body=None, token=None):
    url = API + path
    data = json.dumps(body).encode('utf-8') if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('X-Auth-Token', token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode('utf-8')
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8')
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {'error': raw}


def check(name, cond, detail=''):
    if cond:
        PASSED.append(name)
        print(f'  [PASS] {name}')
    else:
        FAILED.append((name, detail))
        print(f'  [FAIL] {name} :: {detail}')


def login(u, p):
    s, b = req('POST', '/auth/login', {'username': u, 'password': p})
    if s != 200:
        print(f'登录失败 {u}: {b}')
        sys.exit(1)
    return b['token']


def wait_run(token, run_id, timeout=40):
    for _ in range(timeout * 2):
        s, b = req('GET', f'/runs/{run_id}', token=token)
        if s == 200 and b['run']['status'] in ('COMPLETED', 'FAILED'):
            return b['run']
        time.sleep(0.5)
    raise RuntimeError(f'运行 {run_id} 超时')


def near(a, b, tol=0.01):
    return abs(float(a) - float(b)) < tol


def seg_map(segments):
    return {(s['start'], s['end']): s['classification'] for s in segments}


def report_of(token, period):
    return next(r for r in req('GET', '/reports', token=token)[1] if r['period'] == period)


def main():
    print(f'== 目标: {BASE}')
    admin = login('admin', 'admin123')
    analyst = login('analyst', 'analyst123')
    auditor = login('auditor', 'auditor123')
    finance = login('finance', 'finance123')

    # ========== S6 年中收购 + 共享租赁设备（武汉 2025-07） ==========
    print('\n[S6] 年中收购 + 共享租赁：责任片段与待裁定缺口')
    # 权限：分析员不能登记主体/裁定，审核员不能登记事件
    s, _ = req('POST', '/ownership/parties',
               {'code': 'X', 'name': 'x', 'partyType': 'GROUP'}, token=analyst)
    check('S6 分析员无权创建责任主体(403)', s == 403, f's={s}')
    s, _ = req('POST', '/ownership/rulings',
               {'plantId': 3, 'periodStart': '2025-07-10', 'periodEnd': '2025-07-10',
                'decision': 'INCLUDE', 'evidenceNo': 'X'}, token=analyst)
    check('S6 分析员无权登记裁定(403)', s == 403, f's={s}')
    s, _ = req('POST', '/ownership/events',
               {'eventType': 'HANDOVER', 'plantId': 3, 'evidenceType': 'HANDOVER',
                'evidenceNo': 'X', 'effectiveDate': '2025-07-21'}, token=auditor)
    check('S6 审核员无权登记责任事件(403)', s == 403, f's={s}')

    # 设备级片段：1-9 排除 / 10-15 待裁定 / 16-20 待裁定 / 21-31 纳入
    s, d = req('GET', '/ownership/segments?plantId=3&period=2025-07&equipmentCode=EQ-WH-01', token=admin)
    m = seg_map(d['segments'])
    check('S6 设备 1-9 排除（出租方运营且未交割）',
          m.get(('2025-07-01', '2025-07-09')) == 'EXCLUDED', str(m))
    check('S6 设备 10-15 待裁定（已交割但出租方仍运营）',
          m.get(('2025-07-10', '2025-07-15')) == 'PENDING', str(m))
    check('S6 设备 16-20 待裁定（停计量未交接）',
          m.get(('2025-07-16', '2025-07-20')) == 'PENDING', str(m))
    check('S6 设备 21-31 纳入（交接完成且法人控制）',
          m.get(('2025-07-21', '2025-07-31')) == 'INCLUDED', str(m))
    # 工厂级片段（柴油，仅法人控制权）：1-9 排除 / 10-31 纳入
    s, d = req('GET', '/ownership/segments?plantId=3&period=2025-07', token=admin)
    m0 = seg_map(d['segments'])
    check('S6 工厂级 1-9 排除 / 10-31 纳入',
          m0.get(('2025-07-01', '2025-07-09')) == 'EXCLUDED'
          and m0.get(('2025-07-10', '2025-07-31')) == 'INCLUDED', str(m0))

    # 正式运行
    s, run = req('POST', '/runs', {'period': '2025-07'}, token=analyst)
    check('S6 2025-07 运行启动', s == 200, str(run))
    run = wait_run(admin, run['id'])
    rid = run['id']
    check('S6 运行标记含待裁定责任片段', run['hasPendingOwnership'] is True, str(run))

    # 切分守恒与分类
    s, allocs = req('GET', f'/ownership/runs/{rid}/allocations', token=admin)
    elec = [a for a in allocs if a['energyType'] == 'GRID_ELEC']
    diesel = [a for a in allocs if a['energyType'] == 'DIESEL']
    check('S6 电力切分活动量守恒 310000',
          near(sum(a['activityQty'] for a in elec), 310000), str([a['activityQty'] for a in elec]))
    check('S6 柴油切分活动量守恒 3100',
          near(sum(a['activityQty'] for a in diesel), 3100), str([a['activityQty'] for a in diesel]))
    elec_cls = {a['classification']: round(sum(x['activityQty'] for x in elec if x['classification'] == a['classification']), 3)
                for a in elec}
    check('S6 电力 排除90000/待裁定110000/纳入110000',
          near(elec_cls.get('EXCLUDED', 0), 90000) and near(elec_cls.get('PENDING', 0), 110000)
          and near(elec_cls.get('INCLUDED', 0), 110000), str(elec_cls))

    # 结果：待裁定项无排放、不进合计；纳入项正常核算
    s, rd = req('GET', f'/runs/{rid}', token=admin)
    # 期望排放按本次运行绑定的因子快照计算（与因子是否被修订解耦）
    f_elec = next(b['value'] for b in rd['bindings'] if b['factorCode'] == 'ELEC-CNEAST')
    f_diesel = next(b['value'] for b in rd['bindings'] if b['factorCode'] == 'DIESEL-CNEAST')
    exp_inc_elec = 110000 * f_elec / 1000
    exp_inc_diesel = 2200 * f_diesel / 1000
    exp_pending = 110000 * f_elec / 1000
    exp_excluded = 90000 * f_elec / 1000 + 900 * f_diesel / 1000
    exp_included = exp_inc_elec + exp_inc_diesel
    pend = [r for r in rd['results'] if r['status'] == 'PENDING_OWNERSHIP']
    check('S6 待裁定结果无排放值（不并入任一集团趋势）',
          len(pend) == 1 and pend[0]['emissionTco2e'] is None
          and near(pend[0]['activityQty'], 110000), json.dumps(pend))
    inc_elec = next(r for r in rd['results']
                    if r['energyType'] == 'GRID_ELEC' and r['classification'] == 'INCLUDED')
    inc_diesel = next(r for r in rd['results']
                      if r['energyType'] == 'DIESEL' and r['classification'] == 'INCLUDED')
    check('S6 纳入电力排放=110000×绑定电力因子',
          near(inc_elec['emissionTco2e'], exp_inc_elec), str(inc_elec['emissionTco2e']))
    check('S6 纳入柴油排放=2200×绑定柴油因子',
          near(inc_diesel['emissionTco2e'], exp_inc_diesel), str(inc_diesel['emissionTco2e']))

    # 集团汇总：纳入/排除/待裁定
    s, con = req('GET', '/ownership/consolidated?period=2025-07', token=admin)
    t = con['totals']
    check('S6 集团纳入口径正确', near(t['includedTco2e'], exp_included), str(t))
    check('S6 集团排除口径正确（pro forma 参照）', near(t['excludedTco2e'], exp_excluded), str(t))
    check('S6 集团待裁定口径正确', near(t['pendingTco2e'], exp_pending), str(t))
    check('S6 待裁定片段数=2', t['pendingSegmentCount'] == 2, str(t))

    # 报告：待裁定阻断审批/发布
    s, rep = req('POST', '/reports',
                 {'title': '2025年7月集团碳排放披露报告', 'period': '2025-07', 'runId': rid}, token=analyst)
    check('S6 报告创建且保持待核', s == 200 and rep['status'] == 'RECONCILE_FAILED', str(rep))
    rep_id = rep['id']
    s, rd7 = req('GET', f'/reports/{rep_id}', token=admin)
    v1 = rd7['versions'][0]
    check('S6 版本勾稽状态 PENDING_OWNERSHIP',
          v1['reconcileStatus'] == 'PENDING_OWNERSHIP' and v1['pendingOwnershipCount'] == 2, str(v1))
    check('S6 披露条目只含纳入口径（无待裁定/排除）',
          all(i.get('itemStatus', 'OK') != 'PENDING_OWNERSHIP' for i in rd7['items'])
          and near(v1['totalTco2e'], exp_included), str(v1.get('totalTco2e')))
    s, _ = req('POST', f'/reports/{rep_id}/approve', token=finance)
    check('S6 待裁定报告审批被拒(409)', s == 409, f's={s}')
    s, _ = req('POST', f'/reports/{rep_id}/publish', token=finance)
    check('S6 待裁定报告发布被拒(409)', s == 409, f's={s}')
    # 可比性注释 + 版本责任汇总
    s, vo = req('GET', f'/ownership/versions/{v1["id"]}', token=admin)
    note_types = {n['noteType'] for n in vo['notes']}
    check('S6 可比性注释含 待裁定缺口/年中收购/共享租赁/排除',
          {'PENDING_GAP', 'ACQUISITION', 'SHARED_EQUIPMENT', 'EXCLUSION'} <= note_types, str(note_types))
    check('S6 版本责任汇总 待裁定口径正确且固化截止点存在',
          near(vo['summary']['pendingTco2e'], exp_pending) and vo['summary']['derivedAsOf'], str(vo['summary']))
    v1_pending = vo['summary']['pendingTco2e']

    # ========== S7 责任裁定 → 比较版本，原范围保留 ==========
    print('\n[S7] 责任裁定纳入待裁定区间 → 冻结后比较版本')
    s, ruling = req('POST', '/ownership/rulings', {
        'plantId': 3, 'equipmentCode': 'EQ-WH-01',
        'periodStart': '2025-07-10', 'periodEnd': '2025-07-20',
        'decision': 'INCLUDE', 'evidenceNo': 'RUL-WH-2025-001',
        'reason': '仲裁裁定交接空档由集团承担运营控制'}, token=auditor)
    check('S7 审核员登记裁定纳入', s == 200 and ruling['decision'] == 'INCLUDE', str(ruling))
    # 裁定触发影响评估（自动候选运行）
    time.sleep(1)
    s, impacts = req('GET', '/impacts', token=admin)
    ow_impact = [i for i in impacts if i['triggerType'] == 'OWNERSHIP_RULING' and i['period'] == '2025-07']
    check('S7 裁定自动生成责任影响评估', len(ow_impact) >= 1, str([i['triggerType'] for i in impacts]))

    # 重新运行：待裁定应消失
    s, run2 = req('POST', '/runs', {'period': '2025-07'}, token=analyst)
    run2 = wait_run(admin, run2['id'])
    check('S7 裁定后新运行无待裁定', run2['hasPendingOwnership'] is False, str(run2))
    s, d2 = req('GET', f'/runs/{run2["id"]}', token=admin)
    check('S7 新运行无 PENDING_OWNERSHIP 结果',
          all(r['status'] != 'PENDING_OWNERSHIP' for r in d2['results']), 'still pending')

    # 生成冻结后比较版本（原 v1 范围保留）
    s, cv = req('POST', f'/reports/{rep_id}/comparison-versions',
                {'candidateRunId': run2['id'], 'reason': '7月交接空档裁定纳入，生成比较版本'}, token=finance)
    check('S7 生成比较版本且口径为 POST_FREEZE_COMPARISON',
          s == 200 and cv['comparisonBasis'] == 'POST_FREEZE_COMPARISON'
          and cv['reconcileStatus'] == 'OK' and cv['pendingOwnershipCount'] == 0, str(cv))
    s, rd7b = req('GET', f'/reports/{rep_id}', token=admin)
    versions = rd7b['versions']
    check('S7 报告存在两个版本', len(versions) == 2, str(len(versions)))
    v1_now = next(v for v in versions if v['versionNo'] == 1)
    s, vo1 = req('GET', f'/ownership/versions/{v1_now["id"]}', token=admin)
    check('S7 原版本 v1 责任范围保留（待裁定仍 61.226，不被改写）',
          near(vo1['summary']['pendingTco2e'], v1_pending), str(vo1['summary']))
    # 审批发布比较版本
    s, _ = req('POST', f'/reports/{rep_id}/approve', token=finance)
    check('S7 比较版本审批通过', s == 200, f's={s}')
    s, pub = req('POST', f'/reports/{rep_id}/publish', token=finance)
    check('S7 比较版本发布成功', s == 200 and pub['status'] == 'PUBLISHED', str(pub))
    s, con2 = req('GET', '/ownership/consolidated?period=2025-07', token=admin)
    exp_after_included = 220000 * f_elec / 1000 + 2200 * f_diesel / 1000
    check('S7 发布后待裁定归零', near(con2['totals']['pendingTco2e'], 0), str(con2['totals']))
    check('S7 发布后纳入口径正确（电力220000+柴油2200）',
          near(con2['totals']['includedTco2e'], exp_after_included), str(con2['totals']))

    # ========== S8 冲突证据不能多数票覆盖 + 追溯更正 + 历史复演 ==========
    print('\n[S8] 相反法律证据冲突 → 多数票不能覆盖；追溯更正后解除，历史运行不变')
    # 苏州 2025-08 一批电力（31 天 ×10000）
    s, b = req('POST', '/batches', {
        'plantId': 1, 'category': 'ELECTRICITY', 'period': '2025-08',
        'rows': [{'productLineId': 1, 'energyType': 'GRID_ELEC', 'quantity': 310000}]}, token=analyst)
    check('S8 2025-08 电力批次上传', s == 200, str(b))
    s, bv = req('POST', f"/batches/{b['id']}/verify", token=auditor)
    check('S8 批次核验通过', s == 200 and bv['status'] == 'VERIFIED', str(bv))

    # 对立法人控制权证据：剥离 08-05 vs 收购 08-20（显式冲突）
    s, div = req('POST', '/ownership/events', {
        'eventType': 'DIVESTITURE', 'plantId': 1, 'evidenceType': 'LEGAL',
        'evidenceNo': 'LEGAL-SZ-DIV-2025-0805', 'effectiveDate': '2025-08-05',
        'counterparty': '受让方A'}, token=analyst)
    check('S8 剥离证据登记', s == 200, str(div))
    s, acq = req('POST', '/ownership/events', {
        'eventType': 'ACQUISITION', 'plantId': 1, 'evidenceType': 'LEGAL',
        'evidenceNo': 'LEGAL-SZ-ACQ-2025-0820', 'effectiveDate': '2025-08-20',
        'conflictEventId': div['id'], 'targetPartyId': 1}, token=analyst)
    check('S8 对立收购证据登记并双向标记冲突',
          s == 200 and acq['conflictWith'] == div['id'], str(acq))

    s, d = req('GET', '/ownership/segments?plantId=1&period=2025-08', token=admin)
    m8 = seg_map(d['segments'])
    conflict_seg = next((x for x in d['segments'] if x['classification'] == 'PENDING'), None)
    check('S8 冲突区间 08-05~08-20 待裁定',
          conflict_seg and conflict_seg['start'] == '2025-08-05'
          and conflict_seg['end'] == '2025-08-20', json.dumps(d['segments'], ensure_ascii=False))
    check('S8 待裁定依据注明相反法律证据/不得多数票覆盖',
          conflict_seg and '相反法律证据' in conflict_seg['basis'], conflict_seg and conflict_seg['basis'])

    # 再补一份收购证据（08-10）：多数票也不能覆盖冲突
    s, acq2 = req('POST', '/ownership/events', {
        'eventType': 'ACQUISITION', 'plantId': 1, 'evidenceType': 'LEGAL',
        'evidenceNo': 'LEGAL-SZ-ACQ2-2025-0810', 'effectiveDate': '2025-08-10',
        'targetPartyId': 1}, token=analyst)
    check('S8 补充证据登记', s == 200, str(acq2))
    s, d = req('GET', '/ownership/segments?plantId=1&period=2025-08', token=admin)
    still_pending = any(x['classification'] == 'PENDING' and x['start'] == '2025-08-05'
                        and x['end'] == '2025-08-20' for x in d['segments'])
    check('S8 多数票不能覆盖冲突：08-05~08-20 仍待裁定', still_pending, json.dumps(d['segments'], ensure_ascii=False))

    # 更正前的运行（其截止点固定，后续更正不改变它）
    s, run8a = req('POST', '/runs', {'period': '2025-08'}, token=analyst)
    run8a = wait_run(admin, run8a['id'])
    check('S8 更正前运行含待裁定', run8a['hasPendingOwnership'] is True, str(run8a))
    s, alloc_before = req('GET', f'/ownership/runs/{run8a["id"]}/allocations', token=admin)
    pending_qty_before = sum(a['activityQty'] for a in alloc_before if a['classification'] == 'PENDING')
    check('S8 更正前待裁定活动量 160000（16/31 天）', near(pending_qty_before, 160000), str(pending_qty_before))

    # 追溯更正：CORRECTION 指向剥离事件，原事件标记 RULED 但保留
    s, corr = req('POST', '/ownership/events', {
        'eventType': 'CORRECTION', 'plantId': 1, 'evidenceType': 'CORRECTION',
        'evidenceNo': 'CORR-SZ-2025-0825', 'effectiveDate': '2025-08-25',
        'supersedesEventId': div['id'], 'note': '剥离文件被认定无效，追溯更正'}, token=analyst)
    check('S8 追溯更正事件登记并指向原事件',
          s == 200 and corr['supersedesEventId'] == div['id'], str(corr))
    s, events = req('GET', '/ownership/events?plantId=1', token=admin)
    div_now = next(e for e in events if e['id'] == div['id'])
    check('S8 原剥离事件保留但状态 RULED（不可变、不删除）', div_now['status'] == 'RULED', str(div_now))

    # 更正后冲突解除：整月纳入
    s, d = req('GET', '/ownership/segments?plantId=1&period=2025-08', token=admin)
    all_included = all(x['classification'] == 'INCLUDED' for x in d['segments'])
    check('S8 更正后冲突解除，2025-08 全部纳入', all_included, json.dumps(d['segments'], ensure_ascii=False))
    s, run8b = req('POST', '/runs', {'period': '2025-08'}, token=analyst)
    run8b = wait_run(admin, run8b['id'])
    check('S8 更正后新运行无待裁定', run8b['hasPendingOwnership'] is False, str(run8b))

    # 历史复演：更正前运行的待裁定明细保持不变（不按当前股权重挂）
    s, alloc_after = req('GET', f'/ownership/runs/{run8a["id"]}/allocations', token=admin)
    pending_qty_after = sum(a['activityQty'] for a in alloc_after if a['classification'] == 'PENDING')
    check('S8 历史运行按原截止点复演：待裁定 160000 不变',
          near(pending_qty_after, 160000), f'{pending_qty_before} -> {pending_qty_after}')

    # ========== 汇总 ==========
    print('\n========================================')
    print(f'通过 {len(PASSED)} 项，失败 {len(FAILED)} 项')
    if FAILED:
        for name, detail in FAILED:
            print(f'  失败: {name} :: {detail}')
        sys.exit(1)
    print('责任归属专项验收全部通过 ✔')


if __name__ == '__main__':
    main()
