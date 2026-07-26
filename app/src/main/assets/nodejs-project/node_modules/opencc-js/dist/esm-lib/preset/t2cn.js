import from_hk from "../from/hk.js";
import from_hkp from "../from/hkp.js";
import from_tw from "../from/tw.js";
import from_twp from "../from/twp.js";
import from_jp from "../from/jp.js";
import to_cn from "../to/cn.js";
import dict_CJK_Compatibility_Ideographs from "../dict/CJK_Compatibility_Ideographs.js";
import dict_HKPhrasesRev from "../dict/HKPhrasesRev.js";
import dict_HKVariantsRev from "../dict/HKVariantsRev.js";
import dict_HKVariantsRevPhrases from "../dict/HKVariantsRevPhrases.js";
import dict_TSCharacters from "../dict/TSCharacters.js";
import dict_TSPhrases from "../dict/TSPhrases.js";
import dict_TWPhrasesRev from "../dict/TWPhrasesRev.js";
import dict_TWVariantsRev from "../dict/TWVariantsRev.js";
import dict_TWVariantsRevPhrases from "../dict/TWVariantsRevPhrases.js";

const fromDicts = {
    hk: from_hk,
    hkp: from_hkp,
    tw: from_tw,
    twp: from_twp,
    jp: from_jp
};

const toDicts = {
    cn: to_cn
};

const configs = {
    hk2s: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_HKVariantsRevPhrases, dict_HKVariantsRev], [dict_TSPhrases, dict_TSCharacters]] },
    hk2sp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_HKPhrasesRev, dict_HKVariantsRevPhrases, dict_HKVariantsRev], [dict_TSPhrases, dict_TSCharacters]] },
    t2s: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_TSPhrases, dict_TSCharacters]] },
    tw2s: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_TWVariantsRevPhrases, dict_TWVariantsRev], [dict_TSPhrases, dict_TSCharacters]] },
    tw2sp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_TWPhrasesRev, dict_TWVariantsRevPhrases, dict_TWVariantsRev], [dict_TSPhrases, dict_TSCharacters]] }
};

export {fromDicts as from, toDicts as to, configs};