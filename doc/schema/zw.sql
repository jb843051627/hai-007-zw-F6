-- zw 城市供水水质检测调度与达标管理 -- schema (hai-007)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_007

CREATE TABLE IF NOT EXISTS t_zw_arch_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡供水规模(万吨/日)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增供水规模(万吨/日)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '供水规模折到的保障强度层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂档案卡';

CREATE TABLE IF NOT EXISTS t_zw_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '处置工单号',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号（只认代号不认名字）',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（登记/初检/处置/复核封卷）',
  status int DEFAULT NULL COMMENT '工单落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '工单记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质异常处置工单';

-- 导入批次头：一批一次定论。整批拦截/放行、核实成或不成、归档只读都挂在这张表上；
-- 行表 t_zw_imp_row 顺着 batch_no 往下钻。归档行只追加不修改（archived=1 后任何写口拒绝）。
CREATE TABLE IF NOT EXISTS t_zw_imp_batch (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) NOT NULL COMMENT '检测数据上传批次号（全表唯一）',
  file_name varchar(255) DEFAULT NULL COMMENT '原始 Excel 文件名',
  total_rows int DEFAULT NULL COMMENT '本批数据行数',
  blocked int DEFAULT '0' COMMENT '整批拦截 0全格放行已入库 1存在硬错整批拦在库外',
  block_msg varchar(500) DEFAULT NULL COMMENT '拦截说明（点名第几行什么错）',
  content_hash varchar(64) DEFAULT NULL COMMENT '本批原始行内容指印（同内容重导幂等放行）',
  verify_result int DEFAULT NULL COMMENT '核实结论 1核实成 2核实不成；空=尚未核实',
  verify_reason varchar(500) DEFAULT NULL COMMENT '核实不成原因（核实成时记归档说明，可空）',
  verify_by varchar(64) DEFAULT NULL COMMENT '核实人（以核实那一次写下的为准）',
  verify_time datetime DEFAULT NULL COMMENT '核实时刻（系统日志同刻可翻到是哪一次写的）',
  archived int DEFAULT '0' COMMENT '归档标识 0未归档 1已归档只读（原样锁住不能再改）',
  archive_time datetime DEFAULT NULL COMMENT '归档时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zw_imp_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检测数据导入批次头（一批一次核实）';

CREATE TABLE IF NOT EXISTS t_zw_imp_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '检测数据上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次',
  site_no varchar(64) DEFAULT NULL COMMENT '采样点位代号（随样本入账，水质核实只认代号）',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项代号（读数明细项）',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行检测读数（水位）',
  sample_type varchar(32) DEFAULT NULL COMMENT '样本类别(水源水/出厂水/管网末梢)',
  test_at datetime DEFAULT NULL COMMENT '检测发生那天（用哪条线只看这天，不看今天）',
  grade_level int DEFAULT NULL COMMENT '评价等级 1合格 2关注 3超标 4严重超标；空=未定级(无可用标准)',
  rule_code varchar(64) DEFAULT NULL COMMENT '定级所依标准线代号（服务层回值钉死，写过不再改）',
  rule_eff_date datetime DEFAULT NULL COMMENT '所依标准的生效那天（历史复核只认此编号上下文）',
  declared_rule_code varchar(64) DEFAULT NULL COMMENT 'Excel 里申报的规则代号（与检测当天现行线对不上即整批拦截）',
  raw_line varchar(1000) DEFAULT NULL COMMENT '原始行原文（按原列序逐格拼接，下钻与归档都只看这条原样）',
  valid_flag int DEFAULT '1' COMMENT '是否有效（Excel 随单申报）1有效 0无效',
  reviewed_flag int DEFAULT '0' COMMENT '是否已复核（Excel 随单申报）1已复核 0未复核',
  check_status int DEFAULT '0' COMMENT '校验结论 0待校 1格式合格 2异常(硬错：读数/点位/时间/规则对不上)',
  check_msg varchar(500) DEFAULT NULL COMMENT '校验说明（每条都要说清楚能不能过、为什么）',
  confirm_by varchar(64) DEFAULT NULL COMMENT '异常确认人（生成→确认→核实→归档逐段留痕）',
  confirm_time datetime DEFAULT NULL COMMENT '异常确认时刻',
  verify_result int DEFAULT NULL COMMENT '核实结论（随批次那一次核实逐行回写）1核实成 2核实不成',
  verify_reason varchar(500) DEFAULT NULL COMMENT '核实不成原因（随批次回写）',
  verify_by varchar(64) DEFAULT NULL COMMENT '核实人（随批次回写，以那次为准）',
  verify_time datetime DEFAULT NULL COMMENT '核实时间（随批次回写）',
  archived int DEFAULT '0' COMMENT '归档标识 0未归档 1已归档只读（随批次锁定）',
  archive_time datetime DEFAULT NULL COMMENT '归档时刻',
  status int DEFAULT NULL COMMENT '行落地情形 0未入正式账(整批拦在库外) 1已入库',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（同号重导时旧版行置删留痕）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_zw_imp_row_batch (batch_no, row_no),
  KEY idx_zw_imp_row_site (site_no, test_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检测数据导入核对行（明细台账，报表只照此生成）';

CREATE TABLE IF NOT EXISTS t_zw_lim_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '水质限值判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  sample_type varchar(32) DEFAULT NULL COMMENT '适用样本类别(水源水/出厂水/管网末梢)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '关注档指标上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '超标档指标上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '严重超标档指标上限',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质指标限值分档判定线';

CREATE TABLE IF NOT EXISTS t_zw_sam_plan (
  id bigint NOT NULL COMMENT '主键',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（派单时带入事项条）',
  team_id bigint DEFAULT NULL COMMENT '直挂承接班组(部门主键)；空=派发时按各班组当前负载加权分派',
  period_days int DEFAULT NULL COMMENT '周期自然日数（隔多少天采一次）',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（全站唯一可调档）',
  first_due date DEFAULT NULL COMMENT '首个约定采样日（其后按周期顺排）',
  eff_start datetime DEFAULT NULL COMMENT '计划生效之时',
  eff_end datetime DEFAULT NULL COMMENT '计划停用之时(不含)；空=长期现行',
  content varchar(255) DEFAULT NULL COMMENT '采样事由',
  status int DEFAULT NULL COMMENT '计划情形 0现行 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_plan_no (plan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样检测计划周期底档';

CREATE TABLE IF NOT EXISTS t_zw_sam_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '采样事项号（计划代号-采样日，系统一次生成）',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号（底档带入）',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（底档带入）',
  team_id bigint DEFAULT NULL COMMENT '承接班组(部门主键)；空=派发时按当前负载加权分派后回写',
  due_at datetime DEFAULT NULL COMMENT '约定采样那一天的终结时刻(当日23:59:59)——全站唯一口径，任何动作不得挪动',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（唯一可调档）',
  dispatch_at datetime DEFAULT NULL COMMENT '真正派出时刻（首次钉库，后到动作不得覆盖）',
  cancel_at datetime DEFAULT NULL COMMENT '撤单时刻',
  content varchar(255) DEFAULT NULL COMMENT '采样事由与受派班组记要',
  hang_reason varchar(255) DEFAULT NULL COMMENT '挂起原因（点位退役/班组停班/无班可派）',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派 2挂起 3撤单 4转作他理',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（撤单/转理留痕追加于此）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_item_no (item_no),
  UNIQUE KEY uk_sam_plan_site_due (plan_no, site_no, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样任务到期派单';

CREATE TABLE IF NOT EXISTS t_zw_source (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案代号',
  site_name varchar(128) DEFAULT NULL COMMENT '档案名称',
  site_type varchar(32) DEFAULT NULL COMMENT '档案类别(水源地/水厂)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档供水规模一档上限(万吨/日)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(万吨/日)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(万吨/日)',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已退役',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂底档';

-- 旧版停供单带手填计数列（sign_mode/need_count/sign_count），与"枚数顺着签核记录点算"不相容，结构重建。
DROP TABLE IF EXISTS t_zw_stop_bill;
CREATE TABLE IF NOT EXISTS t_zw_stop_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '停供/限供申请签核单号（点位代号-年-当年序）',
  site_no varchar(64) DEFAULT NULL COMMENT '水源点代号（只认代号不认名字）',
  bill_year int DEFAULT NULL COMMENT '申请所属自然年（当年序按此归年）',
  year_seq int DEFAULT NULL COMMENT '同一水源点当年第几份申请（第二份当场单独点名）',
  reason varchar(32) DEFAULT NULL COMMENT '停水事由代号 DEPLETED水源枯竭/OVER_LIMIT指标连续超标/REPAIR检修',
  alt_plan varchar(500) DEFAULT NULL COMMENT '停水后的替代供水方案',
  notice_text varchar(500) DEFAULT NULL COMMENT '用户公告文案（按事由从名录带出，不经人手抄录）',
  node_no int DEFAULT NULL COMMENT '当前所在道 0业务提 1水质核 2调度评 3主管准',
  round_no int DEFAULT '1' COMMENT '签核轮次；主管打回一轮旧签核整轮作废，补签另起一轮，两轮不摆在一起',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已终止（不同意/撤回）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stop_bill_no (bill_no),
  KEY idx_stop_site_year (site_no, bill_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停供/限供申请签核单';

-- 签核台账：只追加，任何关口动作都在此落一笔；关口过没过、落了几枚名，全由服务层顺着这张表点算。
CREATE TABLE IF NOT EXISTS t_zw_stop_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '所属签核单',
  round_no int NOT NULL COMMENT '落印轮次（作废轮原样留痕，计数只数当前轮）',
  node_no int NOT NULL COMMENT '关口 0业务提 1水质核 2调度评 3主管准',
  sign_role varchar(32) NOT NULL COMMENT '落名口 BUSINESS经办/WATER_QUA水质核实/DISP_QTY水量调配评估/DISP_NET管网影响评估/DISP_CHIEF调度主管/APPLICANT申请人',
  signer varchar(64) NOT NULL COMMENT '落名人（各评各的，互不替签）',
  action varchar(16) NOT NULL COMMENT '落印动作 AGREE同意/DISAGREE不同意/WITHDRAW撤回/SENDBACK主管打回',
  opinion varchar(500) DEFAULT NULL COMMENT '签核意见',
  sign_time datetime NOT NULL COMMENT '落印时刻（同一分钟两枚仍是两笔）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常（只追加，不置删）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stop_sign_slot (bill_id, round_no, node_no, sign_role),
  KEY idx_stop_sign_bill (bill_id, round_no, node_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停供申请签核台账（只追加）';

-- 初始档案数据：id=0 在用、id=1 已退役
-- 供水规模上限 30/80/200 万吨/日：等于上限算高一档。
INSERT IGNORE INTO t_zw_source (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZS00', '青坪市城北水源地（在用）', '水源地', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW()),
       (1, 'ZS01', '青坪市城南水厂（已退役）', '水厂', '青坪省—青坪市—城南区—望湖街道', 30.00, 80.00, 200.00, 1, 0, 'seed', NOW());

-- 检测计划周期底档种子：
-- PL-ZS00-M：城北水源地月度例行（30 天一周期，提前 3 天浮出，班组不直挂→按负载加权派班）；
-- PL-ZS01-Q：城南水厂季度（直挂 2 号班组；点位已退役，开单环节直接跳过、不生成新派单；
--     若库里还有退役前落下的待派条，跑轮时仍就地转挂起——旧账可查、新账不开）。
INSERT IGNORE INTO t_zw_sam_plan (id, plan_no, site_no, team_id, period_days, amount, first_due, eff_start, eff_end, content, status, del_flag, create_by, create_time)
VALUES (10, 'PL-ZS00-M', 'ZS00', NULL, 30, 3.00, '2026-09-01', '2026-09-01 00:00:00', NULL, '城北水源地月度例行采样', 0, 0, 'seed', NOW()),
       (11, 'PL-ZS01-Q', 'ZS01', 2, 90, 3.00, '2026-09-15', '2026-09-15 00:00:00', NULL, '城南水厂季度采样（点位已退役，开单即挂起）', 0, 0, 'seed', NOW());

-- 水质限值分档判定线种子（出厂水余氯 mg/L）：
-- 100 旧线 CL-FW-2024：上年口径，上限松，严重超标一格早期入册本就留空（空=不设此档，随超标档下推），
--     管到 2026-01-01（不含），现已让位，只作回溯依据，不参与现行评价；
-- 101 新线 CL-FW-2026：今年收紧，自 2026-01-01 00:00:00 起现行——交接日全厂一套口径，旧线管到进程、新线从结局起算；
-- 102/103 水源水、管网末梢各一条，与出厂水各管各的。
-- 读数值与界值相同算高档；顺位相同并行使权时按代号序列排后者胜出（确定性，四季一致）。
INSERT IGNORE INTO t_zw_lim_rule (id, rule_code, rule_name, sample_type, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES (100, 'CL-FW-2024', '出厂水余氯分档线(上年口径)', '出厂水', 0.30, 0.50, NULL, '2024-01-01 00:00:00', '2026-01-01 00:00:00', 10, 1, 0, 'seed', NOW(), '上年宽口径；严重超标档留空即不设此档'),
       (101, 'CL-FW-2026', '出厂水余氯分档线(今年收紧)', '出厂水', 0.20, 0.40, 0.60, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), '今年收紧口径'),
       (102, 'CL-SW-2026', '水源水余氯分档线', '水源水', 0.20, 0.30, 0.50, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), NULL),
       (103, 'CL-PN-2026', '管网末梢余氯分档线', '管网末梢', 0.15, 0.25, 0.40, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), NULL);

-- 停水公告名录（系统字典 zw_stop_notice）：文案按停水事由从名录带出，页面只展示、不另请人抄录。
-- dict_value=停水事由代号，dict_label=名录短名，remark=完整公告模板。
INSERT IGNORE INTO t_sys_dict_data (id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (880001, 1, '水源枯竭停水公告', 'DEPLETED', 'zw_stop_notice', '', 'warning', 'N', '0', 'seed', NOW(),
        '因{site}水源枯竭，定于停水时段暂停供水，请受影响用户提前蓄水，饮用水请煮沸后使用。'),
       (880002, 2, '指标连续超标停水公告', 'OVER_LIMIT', 'zw_stop_notice', '', 'danger', 'N', '0', 'seed', NOW(),
        '因{site}水质指标连续超标，为保障用水安全，定于停水时段暂停供水，恢复时间另行通知。'),
       (880003, 3, '水厂检修停水公告', 'REPAIR', 'zw_stop_notice', '', 'info', 'N', '0', 'seed', NOW(),
        '因{site}设施检修，定于停水时段暂停供水，检修完成后即时恢复，请用户提前做好储水准备。');

-- 城北水源地近三个月水源水余氯检测行：0.62/0.65/0.71，对现行线 CL-SW-2026 连续超标，供第二道核实对照。
-- 三行各属一个批次（历史导入，格式合格、已入库），本期/上期差异按同批次口径点算。
INSERT IGNORE INTO t_zw_imp_row (id, batch_no, row_no, site_no, item_code, qty, sample_type, test_at, grade_level, rule_code, rule_eff_date, valid_flag, reviewed_flag, check_status, check_msg, status, del_flag, create_by, create_time)
VALUES (9001, 'B202607', 1, 'ZS00', 'CL', 0.62, '水源水', '2026-07-18 09:00:00', 4, 'CL-SW-2026', '2026-01-01 00:00:00', 1, 1, 1, '格式合格', 1, 0, 'seed', NOW()),
       (9002, 'B202608', 1, 'ZS00', 'CL', 0.65, '水源水', '2026-08-18 09:00:00', 4, 'CL-SW-2026', '2026-01-01 00:00:00', 1, 1, 1, '格式合格', 1, 0, 'seed', NOW()),
       (9003, 'B202609', 1, 'ZS00', 'CL', 0.71, '水源水', '2026-09-18 09:00:00', 4, 'CL-SW-2026', '2026-01-01 00:00:00', 1, 0, 1, '格式合格', 1, 0, 'seed', NOW());

-- 化验室检测数据导入：菜单与按钮权限（目录 880100，挂在根下；只读到处人人可查，写口单独授）。
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (880100, '检测数据导入核实', '水质检测读数 Excel 导入、异常核实与归档报表', '/zwImpRow/view', 0, 0, 'zw:zwImpRow:view', 1, 'layui-icon layui-icon-form', 60, 0, 'seed', NOW()),
       (880101, '导入明细台账', '批次与逐行明细查询（只读，人人可查归档结果）', '/zwImpRow/list', 0, 880100, 'zw:zwImpRow:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (880102, 'Excel 整批导入', '当月数据整批导入校验，硬错整批拦在库外', '/zwImpRow/import', 0, 880100, 'zw:zwImpRow:import', 2, NULL, NULL, 0, 'seed', NOW()),
       (880103, '异常确认', '把异常标成已确认，进入待核实', '/zwImpRow/confirm', 0, 880100, 'zw:zwImpRow:confirm', 2, NULL, NULL, 0, 'seed', NOW()),
       (880104, '批次核实归档', '一批一次核实，核实成归档锁行、核实不成写原因', '/zwImpRow/verify', 0, 880100, 'zw:zwImpRow:verify', 2, NULL, NULL, 0, 'seed', NOW()),
       (880105, '明细导出', '按点位/有效/复核导出 UTF-8 Excel', '/zwImpRow/export', 0, 880100, 'zw:zwImpRow:export', 2, NULL, NULL, 0, 'seed', NOW());
-- 管理员角色直接挂上全套
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
VALUES (8801001, 488243256161730560, 880100, 'seed', NOW()),
       (8801002, 488243256161730560, 880101, 'seed', NOW()),
       (8801003, 488243256161730560, 880102, 'seed', NOW()),
       (8801004, 488243256161730560, 880103, 'seed', NOW()),
       (8801005, 488243256161730560, 880104, 'seed', NOW()),
       (8801006, 488243256161730560, 880105, 'seed', NOW());
-- 只读到处人人可查：普通「用户」角色也给菜单、台账查询与导出（导出=只读取数），写口不授
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
VALUES (8801011, 488289006124007424, 880100, 'seed', NOW()),
       (8801012, 488289006124007424, 880101, 'seed', NOW()),
       (8801013, 488289006124007424, 880105, 'seed', NOW());

-- 未完结处置单演示：ZS02 名下挂着在办工单，该点的停供申请到第二道即被压住，不得往前。
INSERT IGNORE INTO t_zw_source (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (2, 'ZS02', '青坪市青西水源地（在用·有在办处置单）', '水源地', '青坪省—青坪市—城西区—青西街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW());
INSERT IGNORE INTO t_zw_case_flow (id, biz_no, site_no, stage, status, content, last_action, del_flag, create_by, create_time)
VALUES (9101, 'CF-202609-01', 'ZS02', 1, 1, '余氯异常处置中', '初检', 0, 'seed', NOW());


-- ============================================================================
-- 水质异常判定留痕：异常登记 → 上报（唯一一单）→ 复测环节链 → 盯办流水 → 结案归档
-- 一组表一条主线。登记在检测行落账的同一事务里自动带出来（读哪条线、超了多少、
-- 关注还是超标、要不要停水/复测全钉成快照）；上报单同点位同日同指标唯一；
-- 复测与盯办两张台账只追加，页面没有任何能改、能撤、能改名的旁路。
-- ============================================================================

-- 供水户联系人名册：上报反映到供水户名下时从这里取人，取号/停用状态随单快照。
-- 上报后"卡住不动多半是联系人取错或联系不上"——盯办就盯这个，名册可订正、已落快照不动。
CREATE TABLE IF NOT EXISTS t_zw_contact (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) NOT NULL COMMENT '所属点位代号（只认代号）',
  account_no varchar(64) DEFAULT NULL COMMENT '供水户编号',
  account_name varchar(128) DEFAULT NULL COMMENT '供水户名称',
  contact_name varchar(64) DEFAULT NULL COMMENT '联系人姓名',
  contact_phone varchar(32) DEFAULT NULL COMMENT '联系电话（空号/停机即联系不上）',
  status int DEFAULT '0' COMMENT '情形 0可用 1停用(取错/失联作废，留痕不删)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_zw_contact_site (site_no, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供水户联系人名册';

-- 异常判定登记：异常一被判出来就在检测行落账同事务自动带出一条，谁也不用手填。
-- 读的是哪条线、线当天的三档界值、超了多少，全在这里钉成快照——日后线改了、人换了，
-- 打开本条看到的仍是当天那一整套判定。同一条检测行只登记一次（uk_anom_row 兜底）。
-- 更正只改本条、不新增；已归档/已结案/已反映供水户的，任何写口 0 行被拒。
CREATE TABLE IF NOT EXISTS t_zw_anomaly (
  id bigint NOT NULL COMMENT '主键',
  anom_no varchar(64) NOT NULL COMMENT '异常登记号（点位-年月日-指标，系统一次生成）',
  imp_row_id bigint NOT NULL COMMENT '原始检测记录行（t_zw_imp_row.id，下钻回当日原账）',
  batch_no varchar(64) DEFAULT NULL COMMENT '检测批次号（随原始行快照）',
  site_no varchar(64) NOT NULL COMMENT '哪个点位',
  item_code varchar(64) NOT NULL COMMENT '哪个指标',
  sample_type varchar(32) DEFAULT NULL COMMENT '样本类别(水源水/出厂水/管网末梢)',
  test_day date NOT NULL COMMENT '哪天的水（按检测日归日，唯一性与重复上报都只认这一天）',
  test_at datetime DEFAULT NULL COMMENT '检测发生时刻（用哪条线只看此刻）',
  qty decimal(12,2) DEFAULT NULL COMMENT '当天检测值',
  grade_level int DEFAULT NULL COMMENT '判定档位 2关注 3超标 4严重超标（登记即钉死）',
  rule_code varchar(64) DEFAULT NULL COMMENT '哪条标准线（唯一定级出口回值钉死）',
  rule_eff_date datetime DEFAULT NULL COMMENT '该标准当天生效之日（用的是哪张表钉死）',
  rule_name varchar(128) DEFAULT NULL COMMENT '标准线名快照',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '当天该线关注档上限快照',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '当天该线超标档上限快照',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '当天该线严重超标档上限快照',
  over_amt decimal(12,2) DEFAULT NULL COMMENT '超了多少（检测值减去所越界值，等于界值算超）',
  base_th decimal(12,2) DEFAULT NULL COMMENT '越的是哪一档界值（与 over_amt 一对）',
  need_retest int DEFAULT '0' COMMENT '要不要复测 1要 0不要（按档位钉死）',
  need_stop int DEFAULT '0' COMMENT '要不要停水 1要 0不要（严重超标档钉死）',
  judge_text varchar(500) DEFAULT NULL COMMENT '判定完整过程文本（取线→比档→超额→处置，原样成句）',
  report_id bigint DEFAULT NULL COMMENT '已挂上报单主键（同一条异常只挂一个，上报后回写）',
  reported int DEFAULT '0' COMMENT '是否已上报 0否 1是（报过即只读）',
  reflected int DEFAULT '0' COMMENT '是否已反映到供水户名下 0否 1是（反映后不可更正）',
  closed int DEFAULT '0' COMMENT '是否已结案 0否 1是（结案链路走完，登记只读）',
  archived int DEFAULT '0' COMMENT '归档标识 0未归档 1已归档（归档那一次写下的为准，任何写口拒绝）',
  archive_time datetime DEFAULT NULL COMMENT '归档时刻（结案同刻落印）',
  correct_times int DEFAULT '0' COMMENT '判定已更正过几轮（改本条内容，逐轮留痕在日志）',
  last_correct_by varchar(64) DEFAULT NULL COMMENT '最近一次更正人',
  last_correct_time datetime DEFAULT NULL COMMENT '最近一次更正时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（登记不删，列只为齐底座）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者（系统自动登记时为落账操作人）',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_zw_anom_no (anom_no),
  UNIQUE KEY uk_zw_anom_row (imp_row_id),
  KEY idx_zw_anom_site_day (site_no, test_day, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质异常判定登记（自动带出、过程全快照）';

-- 异常上报单：同一笔异常只能上报一次（uk_anom_id）；同点位同日同指标第二单整单不成立
-- （uk_site_day_item 物理兜底，不靠页面自觉）。报过之后台账只读：不能改、不能撤、不能改名；
-- 能"回退"的只有结案那一段（退回上一步），上报本身不可撤。登记/上报/归档同刻被改时，
-- 结案归档那一次条件更新写下的为准，系统日志同刻翻得到是哪一次写的。
CREATE TABLE IF NOT EXISTS t_zw_anom_report (
  id bigint NOT NULL COMMENT '主键',
  report_no varchar(64) NOT NULL COMMENT '上报单号（点位-年-当年序）',
  anom_id bigint NOT NULL COMMENT '所挂异常登记（一条异常只挂一个上报单）',
  site_no varchar(64) NOT NULL COMMENT '点位代号',
  item_code varchar(64) NOT NULL COMMENT '指标代号',
  test_day date NOT NULL COMMENT '检测归属日（重复上报判重只认这一天）',
  bill_year int DEFAULT NULL COMMENT '上报所属自然年（当年序按此归年）',
  year_seq int DEFAULT NULL COMMENT '同点位当年第几单',
  grade_level int DEFAULT NULL COMMENT '上报时档位快照',
  need_retest int DEFAULT '0' COMMENT '要不要复测快照',
  need_stop int DEFAULT '0' COMMENT '要不要停水快照',
  step_no int DEFAULT '0' COMMENT '当前处置步 0内部处置 1已反映供水户 2复测回程 3待结案（只由系统事件推进）',
  account_no varchar(64) DEFAULT NULL COMMENT '反映到的供水户编号（反映后钉快照）',
  account_name varchar(128) DEFAULT NULL COMMENT '供水户名称快照',
  contact_id bigint DEFAULT NULL COMMENT '当时取的联系人名册主键（取错也留着，盯办照此点名）',
  contact_name varchar(64) DEFAULT NULL COMMENT '联系人姓名快照',
  contact_phone varchar(32) DEFAULT NULL COMMENT '联系人电话快照',
  reflect_by varchar(64) DEFAULT NULL COMMENT '反映操作人',
  reflect_time datetime DEFAULT NULL COMMENT '反映时刻（自此登记不可更正、前段不可倒改）',
  retest_id bigint DEFAULT NULL COMMENT '自动开出的复测单',
  close_opinion varchar(500) DEFAULT NULL COMMENT '结案结论（归档那一次写下的为准）',
  closed_by varchar(64) DEFAULT NULL COMMENT '结案归档人',
  closed_time datetime DEFAULT NULL COMMENT '结案归档时刻',
  status int DEFAULT '0' COMMENT '单据情形 0在办 1已结案归档 2已退回(结案段回退作废，整单终态留痕)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（上报单不删）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zw_report_no (report_no),
  UNIQUE KEY uk_zw_report_anom (anom_id),
  UNIQUE KEY uk_zw_report_site_day_item (site_no, test_day, item_code),
  KEY idx_zw_report_site_year (site_no, bill_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质异常上报单（一笔异常一单、报过只读）';

-- 复测单头：上报一落账，需要复测的（超标/严重超标档）系统自动从此异常开一条，不由人手建。
-- 固定四环顺序走完，环数与每环经办人照环节开通（zw_retest_node_owner 字典绑定登录名）。
CREATE TABLE IF NOT EXISTS t_zw_retest (
  id bigint NOT NULL COMMENT '主键',
  retest_no varchar(64) NOT NULL COMMENT '复测单号（随上报单号派生，第N轮带 -RN）',
  report_id bigint NOT NULL COMMENT '来源上报单（一条上报每轮一条复测，旧轮原样留着）',
  anom_id bigint NOT NULL COMMENT '来源异常登记',
  site_no varchar(64) NOT NULL COMMENT '点位代号',
  item_code varchar(64) NOT NULL COMMENT '复测指标',
  round_no int DEFAULT '1' COMMENT '复测轮次；某环否决后本轮作废留痕，另起一轮从首环重填',
  node_count int DEFAULT '4' COMMENT '环节总数（固定四环，建单即钉）',
  current_node int DEFAULT '0' COMMENT '当前应填环节 0..3（顺台账点算，不以本列为准）',
  status int DEFAULT '0' COMMENT '复测情形 0待首环 1在办 2已全环走完 3否决卡停(上一环否决后环不开)',
  finish_by varchar(64) DEFAULT NULL COMMENT '封环人',
  finish_time datetime DEFAULT NULL COMMENT '全部走完时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者（系统）',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zw_retest_no (retest_no),
  UNIQUE KEY uk_zw_retest_round (report_id, round_no),
  KEY idx_zw_retest_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='复测单（上报后自动开出）';

-- 复测环节台账：只追加。每环节一行（uk_retest_node 兜底），检测项目/数值/时间/结论
-- 一样都不预填——首环由该环经办人手录，其后每环必须把上一环的结果再填一遍照抄确认，
-- 服务端逐字比对：漏填或对不上整笔不收。提交后本环对其他人只读（owner 也不能改）；
-- 上一环没填完、或结论是否决，下一环不给开。每条记下谁在什么时候填的，报表照得出、回退顺这张表。
CREATE TABLE IF NOT EXISTS t_zw_retest_node (
  id bigint NOT NULL COMMENT '主键',
  retest_id bigint NOT NULL COMMENT '所属复测单',
  node_no int NOT NULL COMMENT '环节序 0采样复测 1化验复核 2水质科判定 3主管封卷',
  owner_login varchar(64) NOT NULL COMMENT '本环节该谁填（系统按环节字典开通，其他人只读）',
  test_item varchar(64) DEFAULT NULL COMMENT '检测项目（首环实填，后环照抄上一环）',
  test_qty decimal(12,2) DEFAULT NULL COMMENT '检测数值（同上，空着等人填，不许预填）',
  test_at datetime DEFAULT NULL COMMENT '检测时间（同上）',
  conclusion varchar(16) DEFAULT NULL COMMENT '复核结论 PASS确认通过/FAIL否决（否决即卡停）',
  opinion varchar(500) DEFAULT NULL COMMENT '本环节意见',
  prev_item varchar(64) DEFAULT NULL COMMENT '开环时系统钉的上一环项目（照抄比对的准据，非人手填）',
  prev_qty decimal(12,2) DEFAULT NULL COMMENT '开环时系统钉的上一环数值',
  prev_test_at datetime DEFAULT NULL COMMENT '开环时系统钉的上一环检测时间',
  prev_conclusion varchar(16) DEFAULT NULL COMMENT '开环时系统钉的上一环结论',
  status int DEFAULT '0' COMMENT '环节情形 0待填 1已提交只读 2否决',
  fill_by varchar(64) DEFAULT NULL COMMENT '提交人（本环节经办人本人）',
  fill_time datetime DEFAULT NULL COMMENT '提交时刻（每条都记名记刻）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常（只追加，不置删）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者（系统开环）',
  create_time datetime DEFAULT NULL COMMENT '开环时刻',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zw_retest_node_slot (retest_id, node_no),
  KEY idx_zw_retest_node_owner (retest_id, owner_login)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='复测环节台账（逐环照抄确认、只追加）';

-- 盯办步次模板：系统走到哪一步就照这张表自动开盯办记录。周期另算——
-- 反映到供水户那一步盯得最紧（2 天一盯），其余步次各按各的自然日。
-- 该找谁盯也钉在本表（登录名），不临到盯的时候再抓阄。
CREATE TABLE IF NOT EXISTS t_zw_follow_template (
  id bigint NOT NULL COMMENT '主键',
  step_no int NOT NULL COMMENT '对应上报单处置步 0内部处置 1反映供水户 2复测回程 3待结案',
  step_name varchar(64) DEFAULT NULL COMMENT '步次名（屏上只许出这套名）',
  due_days int NOT NULL COMMENT '本步盯办周期自然日（与别处各算各的，反映户那一步最短）',
  owner_login varchar(64) NOT NULL COMMENT '本步该谁去盯（登录名，系统按步派）',
  target_contact int DEFAULT '0' COMMENT '本步盯的是不是供水户联系人 1是 0否（内部步盯处置岗）',
  status int DEFAULT '0' COMMENT '模板情形 0现行 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zw_follow_tpl_step (step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='盯办步次与周期模板（系统专用）';

-- 盯办流水：只追加。每一条都由系统在步次推进/跑批到期时自己开、自己结——
-- 没有任何人手新增、撤销、改名的口子。结果必须带上一次盯的时间（prev_follow_at 开条时钉入），
-- 盯了七八次、中间隔几个月，顺着 seq_no 一条条翻得出来；到期没人盯，系统自动结一条"漏盯"
-- 并原样开下一条（漏了什么、当天做了什么都在这张表上）。后段任何写法都倒改不了前段。
CREATE TABLE IF NOT EXISTS t_zw_follow (
  id bigint NOT NULL COMMENT '主键',
  report_id bigint NOT NULL COMMENT '所属上报单',
  step_no int NOT NULL COMMENT '处置步（同模板步次）',
  seq_no int NOT NULL COMMENT '本步第几次盯（1起，漏盯也占号）',
  owner_login varchar(64) NOT NULL COMMENT '该谁去盯（开条时按模板钉）',
  due_at datetime NOT NULL COMMENT '什么时候要盯（开条时刻 + 本步周期）',
  target_account_name varchar(128) DEFAULT NULL COMMENT '盯的供水户（反映户步快照，其余为空）',
  target_contact_name varchar(64) DEFAULT NULL COMMENT '盯的联系人姓名（取错也照记）',
  target_contact_phone varchar(32) DEFAULT NULL COMMENT '盯的联系人电话（停机空号照记）',
  prev_follow_at datetime DEFAULT NULL COMMENT '上一次盯的时间（每条结果都带着它，首盯为空）',
  result varchar(500) DEFAULT NULL COMMENT '盯办结果（谁接的、说了什么；联系不上写明）',
  status int DEFAULT '0' COMMENT '情形 0待盯 1已盯 2联系不上 3漏盯(到期未盯系统自动结)',
  finish_by varchar(64) DEFAULT NULL COMMENT '了结者（经办登记/系统漏盯结 = system）',
  finish_time datetime DEFAULT NULL COMMENT '了结时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常（只追加，不置删）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者（系统）',
  create_time datetime DEFAULT NULL COMMENT '开条时刻',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zw_follow_slot (report_id, step_no, seq_no),
  KEY idx_zw_follow_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='盯办流水（系统自开自结、只追加、回环带上次时刻）';

-- 复测环节经办人绑定：dict_value=环节序，remark=该环经办人登录名（系统按环节开通写权）。
-- 要多少人在本环节填多少：每环只开给绑定的那一个登录名，其他人点进来整环只读。
INSERT IGNORE INTO t_sys_dict_data (id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (880011, 1, '环节0采样复测经办', '0', 'zw_retest_node_owner', '', 'primary', 'N', '0', 'seed', NOW(), 'admin'),
       (880012, 2, '环节1化验复核经办', '1', 'zw_retest_node_owner', '', 'primary', 'N', '0', 'seed', NOW(), 'fuce'),
       (880013, 3, '环节2水质科判定经办', '2', 'zw_retest_node_owner', '', 'primary', 'N', '0', 'seed', NOW(), 'admin'),
       (880014, 4, '环节3主管封卷经办', '3', 'zw_retest_node_owner', '', 'primary', 'N', '0', 'seed', NOW(), 'fuce');

-- 盯办步次模板种子：内部处置 7 天一盯；反映供水户后 2 天一盯（盯得最紧、周期另算）；
-- 复测回程 3 天；待结案 7 天。经办人先绑 admin/fuce 演示，上线按岗位改登录名。
INSERT IGNORE INTO t_zw_follow_template (id, step_no, step_name, due_days, owner_login, target_contact, status, del_flag, create_by, create_time)
VALUES (9401, 0, '内部处置盯办', 7, 'admin', 0, 0, 0, 'seed', NOW()),
       (9402, 1, '供水户联系人盯办', 2, 'fuce', 1, 0, 0, 'seed', NOW()),
       (9403, 2, '复测回程盯办', 3, 'admin', 0, 0, 0, 'seed', NOW()),
       (9404, 3, '结案前盯办', 7, 'admin', 0, 0, 0, 'seed', NOW());

-- 供水户联系人种子：ZS00 联系人可用；ZS02 联系人已停机（演示"联系人取错/联系不上"的紧盯场景）。
INSERT IGNORE INTO t_zw_contact (id, site_no, account_no, account_name, contact_name, contact_phone, status, del_flag, create_by, create_time, remark)
VALUES (9301, 'ZS00', 'HU-ZS00', '城北供水营业所', '周联络', '13900000001', 0, 0, 'seed', NOW(), '在用联系人'),
       (9302, 'ZS02', 'HU-ZS02', '青西供水服务点', '失联号', '13900000002', 1, 0, 'seed', NOW(), '号码已停机，反映后需订正名册');

-- 在办演示链路：城北 2026-09-18 水源水余氯 0.71（检测行 9003，严重超标档）
-- 已自动登记、已上报在办、复测单已自动开出停在首环、首步盯办已开。页面打开即可走全程。
INSERT IGNORE INTO t_zw_anomaly (id, anom_no, imp_row_id, batch_no, site_no, item_code, sample_type, test_day, test_at,
  qty, grade_level, rule_code, rule_eff_date, rule_name, th1_max, th2_max, th3_max, over_amt, base_th,
  need_retest, need_stop, judge_text, reported, reflected, closed, archived, correct_times, del_flag, create_by, create_time)
VALUES (9201, 'ZS00-20260918-CL', 9003, 'B202609', 'ZS00', 'CL', '水源水', '2026-09-18', '2026-09-18 09:00:00',
  0.71, 4, 'CL-SW-2026', '2026-01-01 00:00:00', '水源水余氯分档线', 0.20, 0.30, 0.50, 0.21, 0.50,
  1, 1, '检测值0.71依CL-SW-2026（2026-01-01起现行）越过严重超标档界值0.50，超0.21，判严重超标：须复测并停水',
  1, 0, 0, 0, 0, 0, 'seed', NOW());

INSERT IGNORE INTO t_zw_anom_report (id, report_no, anom_id, site_no, item_code, test_day, bill_year, year_seq,
  grade_level, need_retest, need_stop, step_no, retest_id, status, del_flag, create_by, create_time)
VALUES (9201, 'ZS00-2026-1', 9201, 'ZS00', 'CL', '2026-09-18', 2026, 1, 4, 1, 1, 0, 9201, 0, 0, 'seed', NOW());

INSERT IGNORE INTO t_zw_retest (id, retest_no, report_id, anom_id, site_no, item_code, round_no, node_count, current_node, status, del_flag, create_by, create_time)
VALUES (9201, 'ZS00-2026-1-RT', 9201, 9201, 'ZS00', 'CL', 1, 4, 0, 0, 0, 'seed', NOW());

-- 复测首环（采样复测）随演示单开出：经办人 admin，四件套一律空着等人手填
INSERT IGNORE INTO t_zw_retest_node (id, retest_id, node_no, owner_login, status, del_flag, create_by, create_time)
VALUES (920111, 9201, 0, 'admin', 0, 0, 'seed', NOW());

INSERT IGNORE INTO t_zw_follow (id, report_id, step_no, seq_no, owner_login, due_at, target_account_name, target_contact_name,
  target_contact_phone, prev_follow_at, result, status, finish_by, finish_time, del_flag, create_by, create_time)
VALUES (920101, 9201, 0, 1, 'admin', DATE_ADD(NOW(), INTERVAL 7 DAY), NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 'seed', NOW());

-- 异常判定登记与处置：菜单与按钮权限（目录 880200；只读到处人人可查，写口单独授）。
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (880200, '水质异常判定留痕', '异常自动登记、唯一上报、复测环节链、盯办流水、结案归档全流程留痕', '/zwAnomaly/view', 0, 0, 'zw:zwAnomaly:view', 1, 'layui-icon layui-icon-alert', 61, 0, 'seed', NOW()),
       (880201, '异常台账查询', '异常登记与上报台账查询（只读，人人可查当日标准与判定过程）', '/zwAnomaly/list', 0, 880200, 'zw:zwAnomaly:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (880202, '判定更正', '上条判定错了只改本条内容；已上报/已反映/已结案的改不动', '/zwAnomaly/correct', 0, 880200, 'zw:zwAnomaly:correct', 2, NULL, NULL, 0, 'seed', NOW()),
       (880203, '异常上报', '同一笔异常只能上报一次，同点位同日同指标第二单整单不成立', '/zwAnomaly/report', 0, 880200, 'zw:zwAnomaly:report', 2, NULL, NULL, 0, 'seed', NOW()),
       (880204, '反映供水户', '把上报反映到供水户名下，联系人取号快照，反映后前段锁定', '/zwAnomaly/reflect', 0, 880200, 'zw:zwAnomaly:reflect', 2, NULL, NULL, 0, 'seed', NOW()),
       (880205, '结案与回退', '归档结案以本次写下为准；可从最后一步退回上一步逐条对账', '/zwAnomaly/close', 0, 880200, 'zw:zwAnomaly:close', 2, NULL, NULL, 0, 'seed', NOW()),
       (880206, '复测环节填报', '按环节登录名开通写权，逐环照抄确认，提交后本环只读', '/zwRetest/fill', 0, 880200, 'zw:zwRetest:fill', 2, NULL, NULL, 0, 'seed', NOW()),
       (880207, '盯办结果登记', '盯办记录只由系统开结；本口仅登记本次盯办结果', '/zwFollow/finish', 0, 880200, 'zw:zwFollow:finish', 2, NULL, NULL, 0, 'seed', NOW()),
       (880208, '盯办台账查询', '盯办流水只读查询与漏盯报表导出', '/zwFollow/list', 0, 880200, 'zw:zwFollow:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (880209, '联系人名册维护', '供水户联系人取号维护（订正不影响已落快照）', '/zwContact/edit', 0, 880200, 'zw:zwContact:edit', 2, NULL, NULL, 0, 'seed', NOW()),
       (880210, '盯办跑批推进', '系统到期自动结漏盯、开下一条（定时任务/手动触发同一口）', '/zwFollow/runDue', 0, 880200, 'zw:zwFollow:run', 2, NULL, NULL, 0, 'seed', NOW());

INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
VALUES (8802001, 488243256161730560, 880200, 'seed', NOW()),
       (8802002, 488243256161730560, 880201, 'seed', NOW()),
       (8802003, 488243256161730560, 880202, 'seed', NOW()),
       (8802004, 488243256161730560, 880203, 'seed', NOW()),
       (8802005, 488243256161730560, 880204, 'seed', NOW()),
       (8802006, 488243256161730560, 880205, 'seed', NOW()),
       (8802007, 488243256161730560, 880206, 'seed', NOW()),
       (8802008, 488243256161730560, 880207, 'seed', NOW()),
       (8802009, 488243256161730560, 880208, 'seed', NOW()),
       (8802010, 488243256161730560, 880209, 'seed', NOW()),
       (8802011, 488243256161730560, 880210, 'seed', NOW());
-- 只读到处人人可查：普通「用户」角色给菜单、台账查询与盯办流水；写口按环节登录名在服务层再卡一道，
-- 环节填报/盯办登记两柄放给用户角色（绑给谁由环节字典/盯办模板钉，不是有柄就能填别人的环）
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id, create_by, create_time)
VALUES (8802021, 488289006124007424, 880200, 'seed', NOW()),
       (8802022, 488289006124007424, 880201, 'seed', NOW()),
       (8802023, 488289006124007424, 880208, 'seed', NOW()),
       (8802024, 488289006124007424, 880206, 'seed', NOW()),
       (8802025, 488289006124007424, 880207, 'seed', NOW());

-- 上报处置动作台账：只追加。上报、反映供水户、复测自动开出/走完/否决、结案归档、结案回退
-- 每一步都在此落一笔；已结案的来龙去脉从最后一笔倒着翻到第一笔，逐笔与当时经办屏对得上。
-- 同一条上报的流水不许拿着后段倒改前段：本表没有任何 update/delete 写口。
CREATE TABLE IF NOT EXISTS t_zw_report_action (
  id bigint NOT NULL COMMENT '主键',
  report_id bigint NOT NULL COMMENT '所属上报单',
  seq_no int NOT NULL COMMENT '本单第几步（1起，回退也占号，动作先后只认这个序）',
  action varchar(32) NOT NULL COMMENT '动作 REPORT上报/REFLECT反映供水户/RETEST_OPEN复测开出/RETEST_DONE复测走完/RETEST_VETO复测否决/CLOSE结案归档/CLOSE_ROLLBACK结案回退',
  step_no int DEFAULT NULL COMMENT '动作发生时所在处置步',
  detail varchar(500) DEFAULT NULL COMMENT '本步记事（取的哪个联系人、复测结论、结案结论等）',
  actor varchar(64) NOT NULL COMMENT '经办人（系统自动动作记 system）',
  act_time datetime NOT NULL COMMENT '动作时刻（同一分钟两笔仍是两笔）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常（只追加，不置删）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_zw_report_action_slot (report_id, seq_no),
  KEY idx_zw_report_action_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上报处置动作台账（只追加）';

-- 演示链路的头两笔动作（上报 → 内部处置在办）
INSERT IGNORE INTO t_zw_report_action (id, report_id, seq_no, action, step_no, detail, actor, act_time, del_flag)
VALUES (920101, 9201, 1, 'REPORT', 0, '严重超标异常上报：须复测并停水', 'seed', NOW(), 0);
