import from_cn from "../from/cn.js";
import from_hk from "../from/hk.js";
import from_hkp from "../from/hkp.js";
import from_tw from "../from/tw.js";
import from_twp from "../from/twp.js";
import from_jp from "../from/jp.js";
import to_cn from "../to/cn.js";
import to_hk from "../to/hk.js";
import to_hkp from "../to/hkp.js";
import to_tw from "../to/tw.js";
import to_twp from "../to/twp.js";
import to_jp from "../to/jp.js";
import dict_CJK_Compatibility_Ideographs from "../dict/CJK_Compatibility_Ideographs.js";
import dict_HKPhrases from "../dict/HKPhrases.js";
import dict_HKPhrasesRev from "../dict/HKPhrasesRev.js";
import dict_HKVariants from "../dict/HKVariants.js";
import dict_HKVariantsPhrases from "../dict/HKVariantsPhrases.js";
import dict_HKVariantsRev from "../dict/HKVariantsRev.js";
import dict_HKVariantsRevPhrases from "../dict/HKVariantsRevPhrases.js";
import dict_JPShinjitaiCharacters from "../dict/JPShinjitaiCharacters.js";
import dict_JPShinjitaiCharactersRev from "../dict/JPShinjitaiCharactersRev.js";
import dict_JPShinjitaiPhrases from "../dict/JPShinjitaiPhrases.js";
import dict_STCharacters from "../dict/STCharacters.js";
import dict_STPhrases from "../dict/STPhrases.js";
import dict_STPhrases_GeneratedFromRegionalPhrases from "../dict/STPhrases_GeneratedFromRegionalPhrases.js";
import dict_TSCharacters from "../dict/TSCharacters.js";
import dict_TSPhrases from "../dict/TSPhrases.js";
import dict_TWPhrases from "../dict/TWPhrases.js";
import dict_TWPhrasesRev from "../dict/TWPhrasesRev.js";
import dict_TWVariants from "../dict/TWVariants.js";
import dict_TWVariantsPhrases from "../dict/TWVariantsPhrases.js";
import dict_TWVariantsRev from "../dict/TWVariantsRev.js";
import dict_TWVariantsRevPhrases from "../dict/TWVariantsRevPhrases.js";

const fromDicts = {
    cn: from_cn,
    hk: from_hk,
    hkp: from_hkp,
    tw: from_tw,
    twp: from_twp,
    jp: from_jp
};

const toDicts = {
    cn: to_cn,
    hk: to_hk,
    hkp: to_hkp,
    tw: to_tw,
    twp: to_twp,
    jp: to_jp
};

const configs = {
    hk2s: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_HKVariantsRevPhrases, dict_HKVariantsRev], [dict_TSPhrases, dict_TSCharacters]] },
    hk2sp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_HKPhrasesRev, dict_HKVariantsRevPhrases, dict_HKVariantsRev], [dict_TSPhrases, dict_TSCharacters]] },
    hk2t: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_HKVariantsRevPhrases, dict_HKVariantsRev]] },
    jp2t: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_JPShinjitaiPhrases, dict_JPShinjitaiCharacters]] },
    s2hk: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_HKVariantsPhrases, dict_HKVariants]] },
    s2hkp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_HKPhrases, dict_HKVariantsPhrases, dict_HKVariants]] },
    s2t: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters]] },
    s2tw: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_TWVariantsPhrases, dict_TWVariants]] },
    s2twp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_TWPhrases, dict_TWVariantsPhrases, dict_TWVariants]] },
    t2hk: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_HKVariantsPhrases, dict_HKVariants]] },
    t2jp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_JPShinjitaiCharactersRev]] },
    t2s: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_TSPhrases, dict_TSCharacters]] },
    t2tw: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_TWVariantsPhrases, dict_TWVariants]] },
    tw2s: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_TWVariantsRevPhrases, dict_TWVariantsRev], [dict_TSPhrases, dict_TSCharacters]] },
    tw2sp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_TSPhrases], conversionChain: [[dict_TWPhrasesRev, dict_TWVariantsRevPhrases, dict_TWVariantsRev], [dict_TSPhrases, dict_TSCharacters]] },
    tw2t: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_TWVariantsRevPhrases, dict_TWVariantsRev]] }
};

export {fromDicts as from, toDicts as to, configs};