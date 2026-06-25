package searchengine;

/**
 * PorterStemmer — Martin Porter 词干提取算法 (Porter Stemming Algorithm)
 *
 * 将英文词汇还原为词干 (stem)，实现形态学归一化:
 *   processes  → process
 *   scheduling → schedul
 *   memories   → memori
 *   files      → file
 *   running    → run
 *   created    → creat
 *
 * 算法来源: M.F. Porter, "An algorithm for suffix stripping", Program 14(3), 1980
 * 公有领域实现，被 Lucene / Elasticsearch 等广泛使用。
 *
 * 用法:
 *   PorterStemmer stemmer = new PorterStemmer();
 *   String stem = stemmer.stem("scheduling");  // → "schedul"
 *
 * 创新点 (实验七):
 *   集成到 MapReduce 倒排索引管道中:
 *     建索引时对 token 做 stemming → 索引词统一为词干
 *     查询时对 query  做 stemming → 同词族的多种形态均能命中
 *   例如查 "schedules" 能命中包含 "schedule"、"scheduling"、"scheduled" 的所有文档
 */

public class PorterStemmer {

    private char[] b;
    private int i;    // 当前词末尾偏移
    private int j;    // 词干末尾偏移
    private int k;    // 通用临时变量

    public PorterStemmer() {
        b = new char[64];
    }

    /**
     * 对单词进行词干提取
     * @param word 输入单词 (小写)
     * @return 词干形式
     */
    public String stem(String word) {
        if (word == null || word.length() <= 2) return word;

        // 确保缓冲区足够大
        if (word.length() > b.length) {
            b = new char[word.length() + 10];
        }

        // 复制到工作数组
        for (int n = 0; n < word.length(); n++) {
            b[n] = word.charAt(n);
        }
        i = word.length();
        j = 0;

        if (i > 1) {
            step1();
            step2();
            step3();
            step4();
            step5();
            step6();
        }

        return new String(b, 0, i);
    }

    /* ---- 辅助函数 ---- */

    /** b[j..i) 中是否包含元音 */
    private boolean cons(int idx) {
        switch (b[idx]) {
            case 'a': case 'e': case 'i': case 'o': case 'u': return false;
            case 'y': return (idx == 0) || !cons(idx - 1);
            default:  return true;
        }
    }

    /** 测量 b[0..i) 中元音-辅音序列的数量 (m 值) */
    private int m() {
        int n = 0;
        int s = 0;
        while (true) {
            while (s < i && !cons(s)) s++;        // 跳过元音
            if (s >= i) return n;
            s++;
            while (s < i && cons(s)) s++;          // 跳过辅音
            n++;
            if (s >= i) return n;
        }
    }

    /** b[0..i) 中是否包含元音 */
    private boolean vowelinstem() {
        for (int n = 0; n < i; n++) {
            if (!cons(n)) return true;
        }
        return false;
    }

    /** b[j..i) 是否为双辅音结尾 (如 -ss, -tt, -ll) */
    private boolean doublec(int idx) {
        if (idx < 1) return false;
        return b[idx] == b[idx - 1] && cons(idx);
    }

    /** 检查 b[0..i) 是否为 CVC 模式 (辅-元-辅)，且末尾不是 w/x/y */
    private boolean cvc(int idx) {
        if (idx < 2 || !cons(idx) || cons(idx - 1) || !cons(idx - 2)) return false;
        char ch = b[idx];
        return ch != 'w' && ch != 'x' && ch != 'y';
    }

    /** b[0..i) 是否以字符串 s 结尾 */
    private boolean ends(String s) {
        int l = s.length();
        int o = k - l + 1;
        if (o < 0) return false;
        for (int n = 0; n < l; n++) {
            if (b[o + n] != s.charAt(n)) return false;
        }
        j = o;
        return true;
    }

    /** 将字符串 s 添加到 b[0..k] 末尾，i 后移 */
    private void setto(String s) {
        int l = s.length();
        int o = j + 1;
        for (int n = 0; n < l; n++) b[o + n] = s.charAt(n);
        i = j + l + 1;
    }

    /** b[k] 处被替换为 s */
    private void r(String s) {
        if (m() > 0) setto(s);
    }

    /* ---- 六个步骤 ---- */

    private void step1() {
        // 步骤 1a: 处理复数形式和过去式
        if (b[i - 1] == 's') {
            if (ends("sses"))      { i -= 2; }          // stresses → stress
            else if (ends("ies"))  { i -= 2; }          // ponies → poni
            else if (b[i - 2] != 's') { i--; }          // cats → cat (保留 ss)
        }
        // 步骤 1b: 处理 -ed 和 -ing
        if (ends("eed")) {
            if (m() > 0) i--;                            // agreed → agree
        } else if ((ends("ed") || ends("ing")) && vowelinstem()) {
            i = j;                                       // 去掉 -ed/-ing
            if (ends("at")) setto("ate");                // conflated → conflate
            else if (ends("bl")) setto("ble");           // troubled → trouble
            else if (ends("iz")) setto("ize");           // sized → size
            else if (doublec(i - 1)) {
                i--;                                     // hopped → hop
                char ch = b[i];
                if (ch == 'l' || ch == 's' || ch == 'z') i++;
            } else if (m() == 1 && cvc(i - 1)) {
                setto("e");                              // fil → file
            }
        }
        // 步骤 1c: -y 转 -i
        if (ends("y") && vowelinstem()) {
            b[i - 1] = 'i';                              // happy → happi
        }
    }

    private void step2() {
        k = i - 1;
        if (k < 1) return;   // 需要访问 b[k-1]，至少需要 k>=1 即 i>=2
        switch (b[k - 1]) {
            case 'a': if (ends("ational")) r("ate");        // relational → relate
                      else if (ends("tional"))  r("tion");  // conditional → condition
                      break;
            case 'c': if (ends("enci")) r("ence");           // valenci → valence
                      else if (ends("anci")) r("ance");      // hesitanci → hesitance
                      break;
            case 'e': if (ends("izer")) r("ize"); break;        // digitizer → digitize
            case 'l': if (ends("bli"))  r("ble");            // sensibli → sensible
                      else if (ends("alli"))   r("al");      // formalli → formal
                      else if (ends("entli"))  r("ent");     // differentli → different
                      else if (ends("eli"))    r("e");       // vileli → vile
                      else if (ends("ousli"))  r("ous");     // analogously → analogous
                      break;
            case 'o': if (ends("ization")) r("ize");         // vietnamization → vietnamize
                      else if (ends("ation"))  r("ate");     // predication → predicate
                      else if (ends("ator"))   r("ate");     // operator → operate
                      break;
            case 's': if (ends("alism"))  r("al");            // functionalism → functional
                      else if (ends("iveness")) r("ive");    // decisiveness → decisive
                      else if (ends("fulness")) r("ful");    // hopefulness → hopeful
                      else if (ends("ousness")) r("ous");    // callousness → callous
                      break;
            case 't': if (ends("aliti")) r("al");            // formaliti → formal
                      else if (ends("iviti")) r("ive");      // sensitiviti → sensitive
                      else if (ends("biliti")) r("ble");     // sensibiliti → sensible
                      break;
            case 'g': if (ends("logi")) r("log"); break;     // apologi → apolog
        }
    }

    private void step3() {
        k = i - 1;
        if (k < 1) return;
        switch (b[k]) {
            case 'e': if (ends("icate")) r("ic");           // triplicate → triplic
                      else if (ends("ative")) r("");        // formative → form
                      else if (ends("alize")) r("al");      // formalize → formal
                      break;
            case 'i': if (ends("iciti")) r("ic"); break;    // electriciti → electric
            case 'l': if (ends("ical"))  r("ic");           // electrical → electric
                      else if (ends("ful"))  r("");         // hopeful → hope
                      break;
            case 's': if (ends("ness")) r(""); break;       // goodness → good
        }
    }

    private void step4() {
        k = i - 1;
        if (k < 1) return;   // 需要访问 b[k-1]，至少需要 k>=1 即 i>=2
        switch (b[k - 1]) {
            case 'a': if (ends("al")) { if (m() > 1) i = j; }  // revival → reviv
                      break;
            case 'c': if (ends("ance")) { if (m() > 1) i = j; }
                      else if (ends("ence")) { if (m() > 1) i = j; }
                      break;
            case 'e': if (ends("er")) { if (m() > 1) i = j; }
                      break;
            case 'i': if (ends("ic")) { if (m() > 1) i = j; }
                      break;
            case 'l': if (ends("able")) { if (m() > 1) i = j; }
                      else if (ends("ible")) { if (m() > 1) i = j; }
                      break;
            case 'n': if (ends("ant")) { if (m() > 1) i = j; }
                      else if (ends("ement")) { if (m() > 1) i = j; }
                      else if (ends("ment")) { if (m() > 1) i = j; }
                      else if (ends("ent")) { if (m() > 1) i = j; }
                      break;
            case 'o':
                      if (ends("sion") || ends("tion")) { if (m() > 1) i = j; }
                      else if (ends("ou")) { if (m() > 1) i = j; }
                      break;
            case 's': if (ends("ism")) { if (m() > 1) i = j; }
                      break;
            case 't': if (ends("ate")) { if (m() > 1) i = j; }
                      else if (ends("iti")) { if (m() > 1) i = j; }
                      break;
            case 'u': if (ends("ous")) { if (m() > 1) i = j; }
                      break;
            case 'v': if (ends("ive")) { if (m() > 1) i = j; }
                      break;
            case 'z': if (ends("ize")) { if (m() > 1) i = j; }
                      break;
        }
    }

    private void step5() {
        k = i - 1;
        if (k < 0) return;

        // 步骤 5a
        if (b[k] == 'e') {
            int a = m();
            if (a > 1 || (a == 1 && !cvc(k - 1))) i--;
        }

        // 步骤 5b
        if (m() > 1 && doublec(i - 1) && b[i - 1] == 'l') {
            i--;
        }
    }

    /** 将词干开头的 "y" 转换为 "Y" (在 WordCount 场景中此步骤通常省略) */
    private void step6() {
        // 在标准 Porter 算法中此步处理大写 Y，对于全小写输入可省略
    }
}
