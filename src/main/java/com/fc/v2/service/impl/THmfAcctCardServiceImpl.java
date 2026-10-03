package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.mapper.auto.THmfAcctCardMapper;
import com.fc.v2.mapper.auto.THmfAcctStepLogMapper;
import com.fc.v2.mapper.auto.THmfArchMapper;
import com.fc.v2.model.auto.THmfAcctCard;
import com.fc.v2.model.auto.THmfAcctStepLog;
import com.fc.v2.model.auto.THmfArch;
import com.fc.v2.model.custom.HouseholdAmend;
import com.fc.v2.model.custom.HouseholdFound;
import com.fc.v2.model.custom.HouseholdOpen;
import com.fc.v2.model.custom.HouseholdRequired;
import com.fc.v2.model.custom.HouseholdRoster;
import com.fc.v2.model.custom.HouseholdStep;
import com.fc.v2.model.custom.HouseholdVerdict;
import com.fc.v2.service.ITHmfAcctCardService;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 分户账立户单 Service业务层处理
 *
 * 今年钉死的三句话：一户一张档，一张档不两处挂，一册底册不两家项目共用。
 * 合不合规、处在哪一步、能不能再迈/退、必填哪几栏，只在本类 openHousehold 一族方法里判定，
 * 旧的通用增删改签名虽原样留着，却不再作合规判路（一律不写）——接口层与前端没有第二条路绕。
 *
 * 编号：户号、证载号一律系统发出，人报的号不入库；收费档次不由人报不由人填，
 * 三条界由低往高、建面往上靠、压线取上层，档次与应缴额（建面×每平米标准）同一回折出落卡。
 * 版本：改面/换册另起新版（同户号同链号、版次序+1），旧版沉历史层不露不毁；校验码各版各码，
 * 走到已核对即锁死。步子：待核对→已核对→已冻住，只照步痕重放数当前步，不跨步、冻住即尽头。
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class THmfAcctCardServiceImpl extends ServiceImpl<THmfAcctCardMapper, THmfAcctCard>
        implements ITHmfAcctCardService {

    // ---------------------------------------------------------------- 口径常量

    /** 项目类别：商品房 */
    private static final String TYPE_COMMERCIAL = "商品房";
    /** 项目类别：保障性住房 */
    private static final String TYPE_AFFORDABLE = "保障性住房";
    /** 项目类别：直管公房 */
    private static final String TYPE_PUBLIC = "直管公房";

    /** 底册情形：在册 */
    private static final int ARCH_ACTIVE = 0;
    /** 底册情形：已迁出（不再收新户，底下旧户仍在册仍查得到） */
    private static final int ARCH_MOVED = 1;

    /** 层：现行（名录只露此层） */
    private static final int LAYER_CURRENT = 0;
    /** 层：历史（旧版沉底，只供翻） */
    private static final int LAYER_HISTORY = 1;

    /** 步：待核对（头一步） */
    private static final int STEP_PENDING = 0;
    /** 步：已核对（到此校验码锁死） */
    private static final int STEP_CHECKED = 1;
    /** 步：已冻住（走到即尽头） */
    private static final int STEP_FROZEN = 2;

    /** 步痕动作：过口 */
    private static final int ACT_PASS = 0;
    /** 步痕动作：退回 */
    private static final int ACT_BACK = 1;

    /** 各档每平米收费标准（元/平方米）：档1最低，档4最高；标准由收费口径一处定死。 */
    private static final BigDecimal[] RATE_BY_GRADE = {
            null,
            new BigDecimal("60.00"),
            new BigDecimal("80.00"),
            new BigDecimal("100.00"),
            new BigDecimal("120.00"),
    };

    /** 字段名目：立户单/凭据栏的给人看的叫法（回执点名用）。 */
    private static final String F_DOOR = "门牌";
    private static final String F_QTY = "建筑面积";
    private static final String F_SITE = "所属底册代号";
    private static final String F_TYPE = "项目类别";
    private static final String F_BILL = "户号（由系统发出，不得自拟）";
    private static final String F_CERT = "证载号（由系统发出，不得自拟）";
    private static final String F_DATES = "起写格/止写格";

    /** 迁入旧单时步痕的署名：逐笔实记来路，不笼统记成活人。 */
    private static final String OPERATOR_LEGACY = "纸簿迁入";

    @Resource
    private THmfArchMapper hmfArchMapper;

    @Resource
    private THmfAcctStepLogMapper hmfAcctStepLogMapper;

    /** 落库时刻：生产取当前时刻；留一道口子给定点核验/测试。 */
    protected Date nowTime() {
        return new Date();
    }

    // ---------------------------------------------------------------- 旧读法（签名不动）

    @Override
    public THmfAcctCard selectTHmfAcctCardById(Long id) {
        return this.baseMapper.selectById(id);
    }

    @Override
    public List<THmfAcctCard> selectTHmfAcctCardList(Wrapper<THmfAcctCard> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    // ---------------------------------------------------------------- 旧增删改：签名留着，判路封死

    @Override
    public int insertTHmfAcctCard(THmfAcctCard record) {
        // 立户只认 openHousehold：通用新增不验凭据、不折档、不发号、不留步痕，开口子便绕过今年口径。
        throw new UnsupportedOperationException("立户请走 openHousehold 服务口子");
    }

    @Override
    public int updateTHmfAcctCard(THmfAcctCard record) {
        // 改面走 amendArea、换册走 amendBook、迈步走 pushHousehold；卡面档次/应缴额只读不写。
        throw new UnsupportedOperationException("改面/换册/迈步请走服务层对应口子");
    }

    @Override
    public int deleteTHmfAcctCardByIds(String ids) {
        // 一张档不两处挂、也不整张撤：冻住的撤不掉，未冻住的旧版只许沉历史层。
        throw new UnsupportedOperationException("立户单只添不删，旧版请走版本沉底");
    }

    @Override
    public int deleteTHmfAcctCardById(Long id) {
        throw new UnsupportedOperationException("立户单只添不删，旧版请走版本沉底");
    }

    // ---------------------------------------------------------------- 按类别回话必填栏

    @Override
    public HouseholdRequired requiredFields(String siteType) {
        String type = siteType == null ? null : siteType.trim();
        if (TYPE_COMMERCIAL.equals(type)) {
            return new HouseholdRequired(TYPE_COMMERCIAL,
                    Arrays.asList("paperCertNo", "paperRefNo"),
                    Arrays.asList("预售证载号", "测绘报告编号"));
        }
        if (TYPE_AFFORDABLE.equals(type)) {
            return new HouseholdRequired(TYPE_AFFORDABLE,
                    Arrays.asList("paperCertNo", "paperRefNo"),
                    Arrays.asList("配租配售文号", "承办科室"));
        }
        if (TYPE_PUBLIC.equals(type)) {
            return new HouseholdRequired(TYPE_PUBLIC,
                    Arrays.asList("paperCertNo", "paperRefNo"),
                    Arrays.asList("产权代管单位", "拨交文据"));
        }
        return null;
    }

    // ---------------------------------------------------------------- 立户

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseholdVerdict openHousehold(HouseholdOpen open) {
        if (open == null) {
            return reject(null, "立户意图为空");
        }
        Date now = open.getNow() == null ? nowTime() : open.getNow();

        // 户号、证载号两处编号一律系统发出：人自家拟的号递进来，整单不入库并点名。
        if (!isBlank(open.getBillNo())) {
            return reject(null, F_BILL);
        }
        if (!isBlank(open.getCertNo())) {
            return reject(null, F_CERT);
        }
        if (isBlank(open.getSiteNo())) {
            return reject(null, F_SITE);
        }
        if (isBlank(open.getDoorNo())) {
            return reject(null, F_DOOR);
        }
        THmfArch arch = archByNo(open.getSiteNo().trim());
        if (arch == null) {
            return reject(null, F_SITE + "（册中查无此代号，挂靠只认代号，名字像不算数）");
        }
        if (arch.getStatus() != null && arch.getStatus() == ARCH_MOVED) {
            return new HouseholdVerdict(STEP_PENDING, HouseholdVerdict.CODE_ARCH_GONE,
                    "底册「" + arch.getSiteNo() + "」已迁出，不再收新户，挂不上去", null);
        }
        HouseholdRequired required = requiredFields(arch.getSiteType());
        if (required == null) {
            return reject(null, F_TYPE + "（空着或不在三类排法里）");
        }
        if (open.getQty() == null || open.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            return reject(null, F_QTY + "（填零或填负数不收）");
        }
        if (isBlank(open.getPaperCertNo())) {
            return reject(null, required.getLabels().get(0));
        }
        if (isBlank(open.getPaperRefNo())) {
            return reject(null, required.getLabels().get(1));
        }
        if (open.getWriteStart() != null && open.getWriteEnd() != null
                && open.getWriteStart().after(open.getWriteEnd())) {
            return reject(null, F_DATES + "（起写那一格比止写那一格还晚，得先确认才收）");
        }
        if (arch.getTh1Max() == null || arch.getTh2Max() == null || arch.getTh3Max() == null) {
            return reject(null, "底册三条界没定齐，档次折不出来");
        }

        String siteNo = arch.getSiteNo().trim();
        String doorNo = open.getDoorNo().trim();

        // 一户一张档：同册同门牌即同一户，同时只容一张在走的单。
        THmfAcctCard current = currentCardOf(siteNo, doorNo);
        if (current != null && replay(current).cur < STEP_FROZEN) {
            int cur = replay(current).cur;
            return new HouseholdVerdict(cur, HouseholdVerdict.CODE_DUP,
                    "同一册同一户头一张还在走，第二张立不起来（两个窗口同日各报、换人重报都不许叠）", current);
        }

        String op = operatorOf(open.getOperator());
        THmfAcctCard card;
        String headMsg;
        if (current != null) {
            // 头一张已冻住：占过的名额空出来，同户另起一张——户号沿用，旧版沉历史层。
            card = standUpNextVersion(current, arch, open.getQty(),
                    open.getPaperCertNo().trim(), open.getPaperRefNo().trim(),
                    open.getWriteStart(), open.getWriteEnd(), op, now, open.getRemark());
            headMsg = "前一张已冻住，名额空出，另起一张：第" + card.getNodeNo() + "版";
        } else {
            card = standUpFirst(arch, doorNo, open.getQty(),
                    open.getPaperCertNo().trim(), open.getPaperRefNo().trim(),
                    open.getWriteStart(), open.getWriteEnd(), op, now, open.getRemark());
            headMsg = "已立户：户号" + card.getBillNo();
        }
        return new HouseholdVerdict(STEP_PENDING, HouseholdVerdict.CODE_ACCEPTED,
                headMsg + "，折在第" + card.getGradeLevel()
                        + "层，应缴" + card.getFineAmt().toPlainString() + "元，待核对", card);
    }

    // ---------------------------------------------------------------- 唯一迈步入口

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseholdVerdict pushHousehold(HouseholdStep step) {
        if (step == null || step.getCardId() == null) {
            return null;
        }
        THmfAcctCard card = this.baseMapper.selectById(step.getCardId());
        if (card == null || !normal(card)) {
            return null;
        }
        if (card.getLayerNo() != null && card.getLayerNo() == LAYER_HISTORY) {
            return reject(card, "旧版已沉历史层，不在走；要接着走请先退回该版");
        }
        Date now = step.getNow() == null ? nowTime() : step.getNow();
        String op = operatorOf(step.getOperator());
        Replay state = replay(card);
        int cur = state.cur;

        if (cur >= STEP_FROZEN) {
            return mirror(card, STEP_FROZEN, HouseholdVerdict.CODE_FROZEN,
                    "已冻住：添不进去、字改不动、整张撤不掉，要动只能另起一张");
        }

        boolean backward = step.getAction() != null && step.getAction() < 0;
        Integer expect = step.getExpectStep();
        if (backward) {
            if (cur <= STEP_PENDING) {
                return stay(card, cur, "待核算是头一步，退无可退");
            }
            if (expect != null && expect != cur - 1) {
                return stay(card, cur, "往后只收紧邻的上一步，一次一步；步号以系统回话为准");
            }
            int back = cur - 1;
            int newRound = state.round + 1;
            addLog(card, cur, state.round, ACT_BACK, op, now,
                    isBlank(step.getRemark()) ? "收回上一步重走" : step.getRemark().trim());
            card.setStatus(back);
            card.setUpdateBy(op);
            card.setUpdateTime(now);
            this.baseMapper.updateById(card);
            return new HouseholdVerdict(back, HouseholdVerdict.CODE_ACCEPTED,
                    "已收回" + stepName(back) + "，上一趟记录原样留着，按新一拨重走", card);
        }

        // 往前
        if (expect != null && expect == cur) {
            return new HouseholdVerdict(cur, HouseholdVerdict.CODE_STAY,
                    "本步头一遍已记上，第二遍不另起一行", card);
        }
        if (expect != null && expect > cur + 1) {
            return stay(card, cur, "步子不得跨：往前只走紧邻的下一步");
        }
        if (expect != null && expect < cur) {
            return stay(card, cur, "该步已走过，步号以系统回话为准");
        }
        int target = cur + 1;
        if (target == STEP_CHECKED) {
            // 走到已核对：校验码此刻算出落卡并锁死。已有码的（重走第二拨）必须对得上才认。
            String code = checksum(card);
            if (!isBlank(card.getContent()) && !card.getContent().equals(code)) {
                return stay(card, cur, "校验码对不上：卡面已被人拨过，这一步不能认");
            }
            addLog(card, STEP_CHECKED, state.round, ACT_PASS, op, now, "核对通过，校验码锁死");
            card.setContent(code);
            card.setStatus(STEP_CHECKED);
            card.setUpdateBy(op);
            card.setUpdateTime(now);
            this.baseMapper.updateById(card);
            return new HouseholdVerdict(STEP_CHECKED, HouseholdVerdict.CODE_ACCEPTED,
                    "已核对，校验码" + code + "锁死，此后谁也覆不上去", card);
        }
        if (target == STEP_FROZEN) {
            String code = checksum(card);
            if (isBlank(card.getContent()) || !card.getContent().equals(code)) {
                return stay(card, cur, "未经过核对锁码（或校验码对不上），冻不住");
            }
            addLog(card, STEP_FROZEN, state.round, ACT_PASS, op, now, "冻住，本单到尽头");
            card.setStatus(STEP_FROZEN);
            card.setUpdateBy(op);
            card.setUpdateTime(now);
            this.baseMapper.updateById(card);
            return new HouseholdVerdict(STEP_FROZEN, HouseholdVerdict.CODE_ACCEPTED,
                    "已冻住：添不进、改不动、撤不掉", card);
        }
        return stay(card, cur, "无下一步可走");
    }

    // ---------------------------------------------------------------- 改面 / 换册（另起新版）

    @Override
    public HouseholdVerdict amendArea(HouseholdAmend amend) {
        if (amend != null) {
            amend.setSiteNo(null);
        }
        return amend(amend);
    }

    @Override
    public HouseholdVerdict amendBook(HouseholdAmend amend) {
        return amend(amend);
    }

    private HouseholdVerdict amend(HouseholdAmend amend) {
        if (amend == null || amend.getCardId() == null) {
            return reject(null, "更正意图为空");
        }
        THmfAcctCard old = this.baseMapper.selectById(amend.getCardId());
        if (old == null || !normal(old)) {
            return reject(null, "查无此张现行单");
        }
        if (old.getLayerNo() != null && old.getLayerNo() == LAYER_HISTORY) {
            return reject(old, "这是已沉底的旧版，不能在旧版上改；请对现行版更正，或退回该版");
        }
        Replay state = replay(old);
        if (state.cur >= STEP_FROZEN) {
            return new HouseholdVerdict(STEP_FROZEN, HouseholdVerdict.CODE_FROZEN,
                    "已冻住，字改不动；要动只能另起一张", old);
        }
        boolean changeArea = amend.getQty() != null;
        boolean changeBook = !isBlank(amend.getSiteNo()) && !amend.getSiteNo().trim().equals(old.getSiteNo());
        if (!changeArea && !changeBook) {
            return reject(old, "没有可更的：建面与挂靠底册都没动");
        }
        if (changeArea && amend.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            return reject(old, F_QTY + "（填零或填负数不收）");
        }
        Date now = amend.getNow() == null ? nowTime() : amend.getNow();
        String op = operatorOf(amend.getOperator());

        THmfArch targetArch;
        String paper1;
        String paper2;
        if (changeBook) {
            targetArch = archByNo(amend.getSiteNo().trim());
            if (targetArch == null) {
                return reject(old, F_SITE + "（新底册代号查无）");
            }
            if (targetArch.getStatus() != null && targetArch.getStatus() == ARCH_MOVED) {
                return new HouseholdVerdict(state.cur, HouseholdVerdict.CODE_ARCH_GONE,
                        "新底册「" + targetArch.getSiteNo() + "」已迁出，挂不上去", old);
            }
            HouseholdRequired required = requiredFields(targetArch.getSiteType());
            if (required == null) {
                return reject(old, F_TYPE + "（新底册类别不在三类排法里）");
            }
            if (targetArch.getTh1Max() == null || targetArch.getTh2Max() == null || targetArch.getTh3Max() == null) {
                return reject(old, "新底册三条界没定齐，档次折不出来");
            }
            if (isBlank(amend.getPaperCertNo())) {
                return reject(old, required.getLabels().get(0) + "（换册按新类别补交）");
            }
            if (isBlank(amend.getPaperRefNo())) {
                return reject(old, required.getLabels().get(1) + "（换册按新类别补交）");
            }
            paper1 = amend.getPaperCertNo().trim();
            paper2 = amend.getPaperRefNo().trim();
        } else {
            targetArch = archByNo(old.getSiteNo());
            paper1 = old.getPaperCertNo();
            paper2 = old.getPaperRefNo();
        }

        // 换册后占不占新册的在走名额：同户号在新册若已有现行卡，不许两处挂。
        if (changeBook) {
            boolean clash = currentCardsOf(targetArch.getSiteNo().trim()).stream()
                    .anyMatch(c -> old.getBillNo().equals(c.getBillNo()));
            if (clash) {
                return reject(old, "该户号在新底册下已有一张现行单，一张档不两处挂");
            }
        }

        // 旧版既不留名于名录、也不销毁：沉历史层，码随旧版留着；新版同户号版次序+1。
        THmfAcctCard card = standUpNextVersion(old, targetArch,
                changeArea ? amend.getQty() : old.getQty(), paper1, paper2,
                old.getWriteStart(), old.getWriteEnd(), op, now, amend.getRemark());

        return new HouseholdVerdict(STEP_PENDING, HouseholdVerdict.CODE_ACCEPTED,
                (changeBook ? "已换挂「" + card.getSiteNo() + "」" : "建面已更")
                        + "：旧版（第" + (card.getNodeNo() - 1) + "版）沉历史层，新版第" + card.getNodeNo()
                        + "版折在第" + card.getGradeLevel() + "层、应缴"
                        + card.getFineAmt().toPlainString() + "元，待核对", card);
    }

    /** 头一版立户：系统发户号、证载号，链号即户号，版次序 1。 */
    private THmfAcctCard standUpFirst(THmfArch arch, String doorNo, BigDecimal qty,
                                      String paper1, String paper2, Date writeStart, Date writeEnd,
                                      String op, Date now, String remark) {
        String siteNo = arch.getSiteNo().trim();
        THmfAcctCard card = new THmfAcctCard();
        card.setBillNo(issueBillNo(siteNo));
        card.setCertNo(issueCertNo());
        card.setChainNo(card.getBillNo());
        card.setDoorNo(doorNo);
        card.setNodeNo(1);
        card.setLayerNo(LAYER_CURRENT);
        card.setSiteId(arch.getId() == null ? null : arch.getId().intValue());
        card.setSiteNo(siteNo);
        card.setSiteType(arch.getSiteType().trim());
        card.setQty(PayRowAmounts.normalize(qty));
        card.setPaperCertNo(paper1);
        card.setPaperRefNo(paper2);
        card.setWriteStart(writeStart);
        card.setWriteEnd(writeEnd);
        card.setStatus(STEP_PENDING);
        card.setDelFlag(0);
        card.setCreateBy(op);
        card.setCreateTime(now);
        card.setRemark(remark);
        applyGradeAndFine(card, arch);
        this.baseMapper.insert(card);
        return card;
    }

    /**
     * 另起下一版：旧版沉历史层（不露不毁），同户号同证载号同链号、版次序+1，
     * 码清空（走到已核对重算锁死），档/钱照传入面与目标底册同一回重折，从待核对重新走。
     */
    private THmfAcctCard standUpNextVersion(THmfAcctCard old, THmfArch arch, BigDecimal qty,
                                            String paper1, String paper2, Date writeStart, Date writeEnd,
                                            String op, Date now, String remark) {
        old.setLayerNo(LAYER_HISTORY);
        old.setUpdateBy(op);
        old.setUpdateTime(now);
        this.baseMapper.updateById(old);

        THmfAcctCard card = new THmfAcctCard();
        card.setBillNo(old.getBillNo());
        card.setCertNo(old.getCertNo());
        card.setChainNo(old.getChainNo());
        card.setDoorNo(old.getDoorNo());
        card.setNodeNo((old.getNodeNo() == null ? 1 : old.getNodeNo()) + 1);
        card.setLayerNo(LAYER_CURRENT);
        card.setSiteId(arch.getId() == null ? null : arch.getId().intValue());
        card.setSiteNo(arch.getSiteNo().trim());
        card.setSiteType(arch.getSiteType().trim());
        card.setQty(PayRowAmounts.normalize(qty));
        card.setPaperCertNo(paper1);
        card.setPaperRefNo(paper2);
        card.setWriteStart(writeStart);
        card.setWriteEnd(writeEnd);
        card.setStatus(STEP_PENDING);
        card.setContent(null);
        card.setDelFlag(0);
        card.setCreateBy(op);
        card.setCreateTime(now);
        card.setRemark(remark);
        applyGradeAndFine(card, arch);
        this.baseMapper.insert(card);
        return card;
    }

    // ---------------------------------------------------------------- 查户

    @Override
    public List<HouseholdFound> searchHouseholds(String keyword, List<String> siteNos) {
        if (isBlank(keyword)) {
            return new ArrayList<>();
        }
        // 本级圈定：没给本级范围一个都不摆（默认不给看），别级的户绝不同屏供挑。
        if (siteNos == null || siteNos.isEmpty()) {
            return new ArrayList<>();
        }
        java.util.Set<String> scope = new java.util.HashSet<>();
        for (String s : siteNos) {
            if (!isBlank(s)) {
                scope.add(s.trim());
            }
        }
        if (scope.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, THmfArch> archMap = archMap();
        String kw = keyword.trim().toLowerCase();
        List<HouseholdFound> hits = new ArrayList<>();
        for (THmfAcctCard c : allNormal()) {
            if (c.getLayerNo() != null && c.getLayerNo() != LAYER_CURRENT) {
                continue;
            }
            if (c.getSiteNo() == null || !scope.contains(c.getSiteNo().trim())) {
                continue;
            }
            if (!matchesAny(kw, c.getBillNo(), c.getCertNo(), c.getDoorNo())) {
                continue;
            }
            HouseholdFound f = new HouseholdFound();
            f.setBillNo(c.getBillNo());
            f.setCertNo(c.getCertNo());
            f.setDoorNo(c.getDoorNo());
            f.setSiteNo(c.getSiteNo());
            THmfArch a = c.getSiteNo() == null ? null : archMap.get(c.getSiteNo().trim());
            if (a != null) {
                f.setSiteName(a.getSiteName());
                f.setSiteType(a.getSiteType());
            }
            f.setNodeNo(c.getNodeNo());
            f.setQty(c.getQty());
            f.setGradeLevel(c.getGradeLevel());
            f.setFineAmt(c.getFineAmt());
            f.setStep(replay(c).cur);
            hits.add(f);
        }
        return hits;
    }

    private boolean matchesAny(String kwLower, String... values) {
        for (String v : values) {
            if (v == null) {
                continue;
            }
            String x = v.trim().toLowerCase();
            // 从头匹配、中间片段匹配都给得出。
            if (x.startsWith(kwLower) || x.contains(kwLower)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 本级名录

    @Override
    public HouseholdRoster rosterOf(List<String> siteNos) {
        HouseholdRoster roster = new HouseholdRoster();
        List<String> scope = new ArrayList<>();
        if (siteNos != null) {
            for (String s : siteNos) {
                if (!isBlank(s) && !scope.contains(s.trim())) {
                    scope.add(s.trim());
                }
            }
        }
        roster.setSiteNos(scope);

        // 三笔同一场逐户点算：现行层（旧版已沉底不数）逐户重放当前步，
        // 在走=未冻住，冻住=已冻住，现行=二者之和。
        java.util.Set<String> scopeSet = new java.util.HashSet<>(scope);
        int active = 0;
        int frozen = 0;
        for (THmfAcctCard c : allNormal()) {
            if (c.getLayerNo() != null && c.getLayerNo() != LAYER_CURRENT) {
                continue;
            }
            if (!scopeSet.contains(c.getSiteNo() == null ? null : c.getSiteNo().trim())) {
                continue;
            }
            if (replay(c).cur >= STEP_FROZEN) {
                frozen++;
            } else {
                active++;
            }
        }
        roster.setActiveCount(active);
        roster.setFrozenCount(frozen);
        roster.setCurrentCount(active + frozen);
        roster.setMatched(true);
        return roster;
    }

    // ---------------------------------------------------------------- 版本

    @Override
    public List<THmfAcctCard> historyOf(String billNo) {
        if (isBlank(billNo)) {
            return new ArrayList<>();
        }
        String key = billNo.trim();
        List<THmfAcctCard> out = new ArrayList<>();
        for (THmfAcctCard c : allNormal()) {
            if (key.equals(c.getBillNo() == null ? null : c.getBillNo().trim())) {
                out.add(c);
            }
        }
        out.sort((a, b) -> {
            int na = a.getNodeNo() == null ? 1 : a.getNodeNo();
            int nb = b.getNodeNo() == null ? 1 : b.getNodeNo();
            return Integer.compare(na, nb);
        });
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseholdVerdict revertVersion(Long cardId, String operator) {
        if (cardId == null) {
            return reject(null, "未指明要退回哪一版");
        }
        THmfAcctCard target = this.baseMapper.selectById(cardId);
        if (target == null || !normal(target)) {
            return reject(null, "查无此版");
        }
        if (target.getLayerNo() != null && target.getLayerNo() == LAYER_CURRENT) {
            return reject(target, "这一版本就在现行层，无需退回");
        }
        // 能沉底的版都没冻住；双保险：冻住过的版不许再换回。
        if (replay(target).cur >= STEP_FROZEN) {
            return new HouseholdVerdict(STEP_FROZEN, HouseholdVerdict.CODE_FROZEN,
                    "该版冻住过，封尽了，不能换回现行", target);
        }
        THmfAcctCard current = null;
        for (THmfAcctCard c : allNormal()) {
            if (c.getLayerNo() != null && c.getLayerNo() == LAYER_CURRENT
                    && target.getChainNo() != null
                    && target.getChainNo().equals(c.getChainNo())) {
                current = c;
                break;
            }
        }
        if (current == null) {
            return reject(target, "同链找不着当下现行版，换不回");
        }
        if (replay(current).cur >= STEP_FROZEN) {
            return new HouseholdVerdict(STEP_FROZEN, HouseholdVerdict.CODE_FROZEN,
                    "当下现行版已冻住，旧版换不回来", current);
        }
        Date now = nowTime();
        String op = operatorOf(operator);
        // 只换回那一版：旧数据不去覆新的，两版各自的码、各自的步痕都原样留着。
        current.setLayerNo(LAYER_HISTORY);
        current.setUpdateBy(op);
        current.setUpdateTime(now);
        this.baseMapper.updateById(current);
        target.setLayerNo(LAYER_CURRENT);
        target.setUpdateBy(op);
        target.setUpdateTime(now);
        this.baseMapper.updateById(target);
        return new HouseholdVerdict(replay(target).cur, HouseholdVerdict.CODE_ACCEPTED,
                "已换回第" + target.getNodeNo() + "版；原现行第" + current.getNodeNo()
                        + "版沉回历史层，旧数据没去覆新的", target);
    }

    // ---------------------------------------------------------------- 纸簿旧单迁入

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseholdVerdict importLegacy(THmfAcctCard paper, String operator) {
        if (paper == null) {
            return reject(null, "旧单为空");
        }
        if (isBlank(paper.getBillNo())) {
            return reject(null, "纸上户号空着：迁入沿用纸上的号登记为系统号，不重发");
        }
        if (isBlank(paper.getSiteNo())) {
            return reject(null, F_SITE);
        }
        if (isBlank(paper.getDoorNo())) {
            return reject(null, F_DOOR);
        }
        THmfArch arch = archByNo(paper.getSiteNo().trim());
        if (arch == null) {
            return reject(null, F_SITE + "（册中查无此代号）");
        }
        if (arch.getStatus() != null && arch.getStatus() == ARCH_MOVED) {
            return new HouseholdVerdict(STEP_PENDING, HouseholdVerdict.CODE_ARCH_GONE,
                    "底册已迁出：旧单不能挂到迁出的册下", null);
        }
        HouseholdRequired required = requiredFields(arch.getSiteType());
        if (required == null) {
            return reject(null, F_TYPE);
        }
        if (paper.getQty() == null || paper.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            return reject(null, F_QTY);
        }
        if (isBlank(paper.getPaperCertNo())) {
            return reject(null, required.getLabels().get(0));
        }
        if (isBlank(paper.getPaperRefNo())) {
            return reject(null, required.getLabels().get(1));
        }
        String siteNo = arch.getSiteNo().trim();
        String billNo = paper.getBillNo().trim();

        // 代号与户号配成的一对，不分先后：库里已有这对，只认库里那张。
        for (THmfAcctCard c : allNormal()) {
            if (siteNo.equals(c.getSiteNo() == null ? null : c.getSiteNo().trim())
                    && billNo.equals(c.getBillNo() == null ? null : c.getBillNo().trim())) {
                return new HouseholdVerdict(replay(c).cur, HouseholdVerdict.CODE_DUP,
                        "代号+户号这一对库里已在册，仍旧只那一张，不重录", c);
            }
        }

        Date now = nowTime();
        String op = operatorOf(operator);
        int paperStatus = paper.getStatus() != null
                && (paper.getStatus() == STEP_CHECKED || paper.getStatus() == STEP_FROZEN)
                ? paper.getStatus() : STEP_PENDING;

        THmfAcctCard card = new THmfAcctCard();
        card.setBillNo(billNo);
        // 旧单没有系统证载号的补一发；带着的沿用，不重发。
        card.setCertNo(isBlank(paper.getCertNo()) ? issueCertNo() : paper.getCertNo().trim());
        card.setChainNo(billNo);
        card.setDoorNo(paper.getDoorNo().trim());
        card.setNodeNo(paper.getNodeNo() == null || paper.getNodeNo() < 1 ? 1 : paper.getNodeNo());
        card.setLayerNo(LAYER_CURRENT);
        card.setSiteId(arch.getId() == null ? null : arch.getId().intValue());
        card.setSiteNo(siteNo);
        card.setSiteType(arch.getSiteType().trim());
        card.setQty(PayRowAmounts.normalize(paper.getQty()));
        card.setPaperCertNo(paper.getPaperCertNo().trim());
        card.setPaperRefNo(paper.getPaperRefNo().trim());
        card.setWriteStart(paper.getWriteStart());
        card.setWriteEnd(paper.getWriteEnd());
        card.setStatus(paperStatus);
        card.setDelFlag(0);
        card.setCreateBy(op);
        card.setCreateTime(paper.getCreateTime() == null ? now : paper.getCreateTime());
        card.setRemark(paper.getRemark());
        // 面积动过层数跟着动、钱还是老钱的旧病：迁入即按三条界与标准同一回重折，档与钱一起校正。
        applyGradeAndFine(card, arch);
        this.baseMapper.insert(card);

        // 步痕、版次序、校验码空着的旧单也要接得住：照纸上进展补起步痕。
        // 在走的（待核对/已核对待冻）补出对应过口，但不补到冻住之外——纸上印已冻住才补第二道。
        Date stamp = card.getCreateTime();
        if (paperStatus >= STEP_CHECKED) {
            addLog(card, STEP_CHECKED, 1, ACT_PASS, OPERATOR_LEGACY, stamp, "纸簿旧单补痕：原已核对");
            card.setContent(checksum(card));
        }
        if (paperStatus >= STEP_FROZEN) {
            addLog(card, STEP_FROZEN, 1, ACT_PASS, OPERATOR_LEGACY, stamp, "纸簿旧单补痕：原已冻住");
        }
        if (paperStatus >= STEP_CHECKED) {
            card.setUpdateBy(OPERATOR_LEGACY);
            card.setUpdateTime(stamp);
            this.baseMapper.updateById(card);
        }
        return new HouseholdVerdict(paperStatus, HouseholdVerdict.CODE_ACCEPTED,
                "旧单已接入：户号" + billNo + "沿用，版次序补为第" + card.getNodeNo()
                        + "版，档次/应缴按现界重折为第" + card.getGradeLevel() + "层/"
                        + card.getFineAmt().toPlainString() + "元，步痕补齐", card);
    }

    // ---------------------------------------------------------------- 折档与发号

    /**
     * 三条界由低往高，拿建面往上靠：够不着低界落头一层，够到低界落上一层……
     * 恰好压在界上算跨过去取上面一层。档次、每平米标准、应缴额同一回算出落卡。
     */
    private void applyGradeAndFine(THmfAcctCard card, THmfArch arch) {
        BigDecimal q = card.getQty();
        int grade;
        if (q.compareTo(arch.getTh1Max()) < 0) {
            grade = 1;
        } else if (q.compareTo(arch.getTh2Max()) < 0) {
            grade = 2;
        } else if (q.compareTo(arch.getTh3Max()) < 0) {
            grade = 3;
        } else {
            grade = 4;
        }
        BigDecimal rate = RATE_BY_GRADE[grade];
        card.setGradeLevel(grade);
        card.setRateStd(rate);
        // 应缴额与层数必须同一回算的数：建面×该档每平米标准，归一到分。
        card.setFineAmt(PayRowAmounts.normalize(q.multiply(rate)));
    }

    /** 系统发户号：底册代号-“户”-四位顺序，顺序照该册已发过的不同户号点出。 */
    private String issueBillNo(String siteNo) {
        long seq = allNormal().stream()
                .filter(c -> siteNo.equals(c.getSiteNo() == null ? null : c.getSiteNo().trim()))
                .map(THmfAcctCard::getBillNo)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count() + 1;
        return siteNo + "-H" + String.format("%04d", seq);
    }

    /** 系统发证载号：YS-八位顺序，照全库已发过的证载号点出。 */
    private String issueCertNo() {
        long seq = allNormal().stream()
                .map(THmfAcctCard::getCertNo)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count() + 1;
        return "YS-" + String.format("%08d", seq);
    }

    /**
     * 校验码：版次序、代号、户号、建面、档次、应缴额同一串喂 SHA-256，取前 16 位。
     * 版次一换（node 变）码必变；两版共用一个码即为错。
     */
    private String checksum(THmfAcctCard card) {
        String canonical = "V" + nz(card.getNodeNo())
                + "|" + nz(card.getSiteNo())
                + "|" + nz(card.getBillNo())
                + "|" + (card.getQty() == null ? "" : card.getQty().toPlainString())
                + "|G" + nz(card.getGradeLevel())
                + "|" + (card.getFineAmt() == null ? "" : card.getFineAmt().toPlainString());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("校验码算不出来", e);
        }
    }

    private String nz(Object o) {
        return o == null ? "" : o.toString();
    }

    // ---------------------------------------------------------------- 步痕重放（当前步唯一算法）

    /**
     * 照步痕时间序重放：过口进紧邻下一步、退回收一步并另起一拨。
     * 纸簿旧单一条痕都没有时，不报错：回退到卡上镜像/待核对，在走的旧单不被误当成停了的。
     */
    private Replay replay(THmfAcctCard card) {
        Replay s = new Replay();
        s.cur = STEP_PENDING;
        s.round = 1;
        List<THmfAcctStepLog> logs = new ArrayList<>();
        for (THmfAcctStepLog l : allStepLogs()) {
            if (card.getId() != null && card.getId().equals(l.getCardId())
                    && (l.getDelFlag() == null || l.getDelFlag() == 0)) {
                logs.add(l);
            }
        }
        if (logs.isEmpty()) {
            // 旧单无步痕：照纸上镜像认步（空着算待核对），绝不当成已冻住。
            s.cur = card.getStatus() != null
                    && card.getStatus() >= STEP_PENDING && card.getStatus() <= STEP_FROZEN
                    ? card.getStatus() : STEP_PENDING;
            s.legacy = true;
            return s;
        }
        logs.sort((a, b) -> {
            Date ta = a.getLogTime();
            Date tb = b.getLogTime();
            if (ta == null && tb == null) {
                return Long.compare(a.getId() == null ? 0 : a.getId(), b.getId() == null ? 0 : b.getId());
            }
            if (ta == null) {
                return -1;
            }
            if (tb == null) {
                return 1;
            }
            int c = ta.compareTo(tb);
            return c != 0 ? c : Long.compare(a.getId() == null ? 0 : a.getId(), b.getId() == null ? 0 : b.getId());
        });
        for (THmfAcctStepLog l : logs) {
            int action = l.getAction() == null ? ACT_PASS : l.getAction();
            int step = l.getStepNo() == null ? STEP_PENDING : l.getStepNo();
            int rowRound = l.getRoundNo() == null ? 1 : l.getRoundNo();
            if (action == ACT_BACK) {
                if (rowRound == s.round && step == s.cur && s.cur > STEP_PENDING) {
                    s.cur--;
                    s.round++;
                }
            } else {
                if (rowRound == s.round && step == s.cur + 1) {
                    s.cur = step;
                }
                // 其余为沉底旧拨/重复行，原样留存，不数进现行步。
            }
        }
        return s;
    }

    private static class Replay {
        int cur;
        int round;
        boolean legacy;
    }

    private void addLog(THmfAcctCard card, int step, int round, int action,
                        String operator, Date time, String remark) {
        THmfAcctStepLog l = new THmfAcctStepLog();
        l.setCardId(card.getId());
        l.setChainNo(card.getChainNo());
        l.setNodeNo(card.getNodeNo());
        l.setStepNo(step);
        l.setRoundNo(round);
        l.setAction(action);
        l.setOperator(operator);
        l.setLogTime(time);
        l.setCreateTime(time);
        l.setDelFlag(0);
        l.setRemark(remark);
        this.hmfAcctStepLogMapper.insert(l);
    }

    // ---------------------------------------------------------------- 私有帮手

    private List<THmfAcctCard> allNormal() {
        List<THmfAcctCard> all = this.baseMapper.selectList(new QueryWrapper<THmfAcctCard>()
                .eq("del_flag", 0));
        return all == null ? new ArrayList<>() : all;
    }

    private List<THmfAcctStepLog> allStepLogs() {
        List<THmfAcctStepLog> all = this.hmfAcctStepLogMapper.selectList(
                new QueryWrapper<THmfAcctStepLog>().eq("del_flag", 0));
        return all == null ? new ArrayList<>() : all;
    }

    private List<THmfAcctCard> currentCardsOf(String siteNo) {
        List<THmfAcctCard> out = new ArrayList<>();
        for (THmfAcctCard c : allNormal()) {
            if (c.getLayerNo() != null && c.getLayerNo() != LAYER_CURRENT) {
                continue;
            }
            if (siteNo.equals(c.getSiteNo() == null ? null : c.getSiteNo().trim())) {
                out.add(c);
            }
        }
        return out;
    }

    /** 某册下某门牌（同一户）当下现行层的那张；旧版已沉底，不在此列。 */
    private THmfAcctCard currentCardOf(String siteNo, String doorNo) {
        for (THmfAcctCard c : currentCardsOf(siteNo)) {
            if (doorNo.equals(c.getDoorNo() == null ? null : c.getDoorNo().trim())) {
                return c;
            }
        }
        return null;
    }

    private THmfArch archByNo(String siteNo) {
        if (isBlank(siteNo)) {
            return null;
        }
        for (THmfArch a : archMap().values()) {
            if (siteNo.trim().equals(a.getSiteNo() == null ? null : a.getSiteNo().trim())) {
                return a;
            }
        }
        return null;
    }

    private Map<String, THmfArch> archMap() {
        List<THmfArch> all = this.hmfArchMapper.selectList(new QueryWrapper<THmfArch>()
                .eq("del_flag", 0));
        Map<String, THmfArch> map = new LinkedHashMap<>();
        if (all != null) {
            for (THmfArch a : all) {
                if (a.getSiteNo() != null) {
                    map.putIfAbsent(a.getSiteNo().trim(), a);
                }
            }
        }
        return map;
    }

    private boolean normal(THmfAcctCard c) {
        return c.getDelFlag() == null || c.getDelFlag() == 0;
    }

    private String operatorOf(String passed) {
        String op = isBlank(passed) ? ShiroUtils.getLoginName() : passed.trim();
        return op == null ? OPERATOR_LEGACY : op;
    }

    private HouseholdVerdict reject(THmfAcctCard card, String missingField) {
        return new HouseholdVerdict(
                card == null ? STEP_PENDING : replay(card).cur,
                HouseholdVerdict.CODE_REJECTED,
                "户进不了库，缺/误这一栏：" + missingField, card);
    }

    private HouseholdVerdict stay(THmfAcctCard card, int authoritativeStep, String message) {
        // 步子没挪：不添步痕；镜像校正为系统重放的权威步后回话。
        mirrorStep(card, authoritativeStep);
        return new HouseholdVerdict(authoritativeStep, HouseholdVerdict.CODE_STAY, message, card);
    }

    private HouseholdVerdict mirror(THmfAcctCard card, int authoritativeStep, int code, String message) {
        mirrorStep(card, authoritativeStep);
        return new HouseholdVerdict(authoritativeStep, code, message, card);
    }

    private void mirrorStep(THmfAcctCard card, int authoritativeStep) {
        if (card.getStatus() == null || card.getStatus() != authoritativeStep) {
            card.setStatus(authoritativeStep);
            this.baseMapper.updateById(card);
        }
    }

    private String stepName(int step) {
        switch (step) {
            case STEP_PENDING: return "待核对";
            case STEP_CHECKED: return "已核对";
            case STEP_FROZEN: return "已冻住";
            default: return "待核对";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
