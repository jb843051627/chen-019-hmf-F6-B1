# 分户账立户单 交接说明

最后修订：2026-10-03。本分只讲「分户账立户」这一段及其与册子核销、使用申请的咬合点。

## 一句话口径

一户一张档，一张档不两处挂，一册底册不两家项目共用。所有合规判定只在
`THmfAcctCardServiceImpl` 一个类里，Controller 只收意图、转回话，没有第二条判路。

## 入口与封死的旧路

- 页面入口：`HmfHouseholdController`（`/hmfHousehold/*`）
  - `open` 立户、`push` 唯一迈步入口（往前核对/冻住、往后退回都走它）
  - `amendArea` 改面、`amendBook` 换底册、`revert` 换回旧版、`importLegacy` 纸簿迁入
  - `search` 查户（只摆本级现行户）、`roster` 本级名录三笔、`history` 翻版次
- 通用增删改（`insertTHmfAcctCard` / `updateTHmfAcctCard` / `deleteTHmfAcctCard*`）
  签名留着但一律抛 `UnsupportedOperationException`——立户、改面、迈步、删张都绕不开服务口子。

## 步子：三档链与步痕重放

- 三步：待核对(0) → 已核对(1) → 已冻住(2)。
- 当前步**只照 `t_hmf_acct_step_log` 逐笔重放**（过口进紧邻下一步、退回收一步并拨次+1）；
  卡面 `status` 只是镜像，纸面被人手拨过不算数，回话时镜像校正。
- 往前只进紧邻下一步：`expectStep` 跨步一律 `CODE_STAY`，不添痕、不落卡。
- 往后只收紧邻一步；待核对退无可退。
- **冻住即尽头**：推不动、退不回、改不动、整张撤不掉，回 `CODE_FROZEN`，不添任何痕。
- 步痕逐笔留存，**永不抹除**：过口、退回、新旧各拨各写各的行。
  退回后重走 = 新拨次（round+1），旧拨行沉底但数得出先后；重走与初次凭拨次分辨。

## 校验码

- 走到「已核对」时由 `checksum()` 算出落卡（`content` 列）并锁死。
- 喂 SHA-256 的串只含：版次序、底册代号、户号、建面、档次、应缴额。
  **不掺时刻、不掺经手人**——同一版退回重走多少回，码必须与原先一字不差；
  卡面被改导致对不上则这步不认。版次一变（nodeNo+1）码必变，新旧两串各属各版。

## 版本（改面 / 换册 / 换回）

- `layer_no`：0 现行（名录、查户只露此层），1 历史（旧版沉底，不露不毁，供翻）。
- 改建面或换底册：旧版沉历史层，同户号/证载号/链号、`node_no+1` 另起新版，
  档与应缴额照三条界同一回重折，码清空待重新核对。
- 换册按新底册类别重验凭据，且同户号在新册不得已有现行卡（不两处挂）；已迁出册挂不上。
- `revertVersion` 只换层、不覆数据：两版各自的码与步痕原样留着；冻住过的版换不回。

## 名录三笔（`rosterOf`）

同一场逐户照步痕重放点算，三笔同源、接得平：

- 在册 active = 现行层未冻住户数（立户 +1，**冻住 −1，名额往下松**）
- 冻住 frozen = 现行层已冻住户数
- 现行 current = active + frozen；`matched` 恒按此式校验

## 编号与防重

- 户号（`JZ00-H0001` 状）、证载号（`YS-xxxxxxxx`）一律系统发出，人拟号整单拒收。
- 同册同门牌（=同一户）现行卡未冻住时再立户，回 `CODE_DUP`，库里仍只那一张
  （两个窗口同日各报一遍、一份未冻住另一份压上来，都只认头一张）；
  前一张冻住后才许同户另起下一版。
- 纸簿迁入按「底册代号 + 户号」一对防重，不分先后。
- 底册 `t_hmf_arch.status=1` 为已迁出：不收新户、不接迁入，但底下旧户仍在册、仍查得到。

## 纸簿旧单迁入（`importLegacy`）

- 版次序、步痕、校验码空着也接得住，**不得抛错**：版次序缺省补 1；
  无步痕时照纸上进展认步，空 status 一律认「待核对」（在走），绝不误作已冻住。
- 纸上已核对：补一道过口痕并按现界重折后补锁码；已冻住：补两道痕，冻住封尽照旧。
- 档与应缴额迁入即按现行三条界与标准重折，治「面积动过、钱还是老钱」的旧病。

## 与相邻两段的咬合

- 使用申请单（`THmfUseFlowServiceImpl`）进结算时点户面余额，认行规矩合在
  `HouseholdPaid` 一处：只认已销账、现行册面层、未删的行，挂起/往期/已撤册行不算，
  旧批法无册头行照旧算。改户面口径只动 `HouseholdPaid`。
- 金额归一到库口精度统一走 `PayRowAmounts.normalize`（decimal(12,2)）。

## 测试与本地跑法

- 测试全在 `src/test/java/com/fc/v2/hmf/`，内存假 Mapper，不起库：
  - `HouseholdRegisterTest` 立户/迈步/版本/名录/查户/迁入（23）
  - `UseFlowChainTest` 使用申请四档链与户面余额（24）
  - `BudgetBillApprovalTest` 拨付核签三档链（19）
  - `PayBookReconcileTest` 册子核销（19）、`SubmitBatchTest` 旧批法进门（8）
  - `CycleTaskSweepTest` 定时扫单（30）
- 本环境 target 在 9p 挂载上、资源拷贝会碰权限错，跳过资源拷贝即可：
  `mvn -Dmaven.resources.skip=true -Dtest='HouseholdRegisterTest,UseFlowChainTest,BudgetBillApprovalTest,PayBookReconcileTest,SubmitBatchTest,CycleTaskSweepTest' test`

## 2026-10-03 修掉的四组连锁缺陷（F6-B1）

1. 迈步：跨步不拦、冻住还能往前推/退回/改/撤 → 跨步 STAY、冻住一律 CODE_FROZEN。
2. 名录：冻住不减一、屏上总数与册子对不上 → active 冻住减一，current=active+frozen。
3. 留痕/校验码：退回抹光旧痕重走无从分辨；码掺 currentTimeMillis 同版两码 →
   永不抹痕、拨次分存；码只吃卡面定项，同版同码。
4. 旧单迁入：版次序/码空即抛错、空 status 误作冻住；同户两窗口叠单 →
   空值接住补痕、缺码待核对不补锁、同户在办 CODE_DUP。
