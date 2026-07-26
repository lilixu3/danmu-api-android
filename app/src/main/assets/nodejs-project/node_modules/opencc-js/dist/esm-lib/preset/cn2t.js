import from_cn from "../from/cn.js";
import to_hk from "../to/hk.js";
import to_hkp from "../to/hkp.js";
import to_tw from "../to/tw.js";
import to_twp from "../to/twp.js";
import to_jp from "../to/jp.js";
import dict_CJK_Compatibility_Ideographs from "../dict/CJK_Compatibility_Ideographs.js";
import dict_HKPhrases from "../dict/HKPhrases.js";
import dict_HKVariants from "../dict/HKVariants.js";
import dict_HKVariantsPhrases from "../dict/HKVariantsPhrases.js";
import dict_STCharacters from "../dict/STCharacters.js";
import dict_STPhrases from "../dict/STPhrases.js";
import dict_STPhrases_GeneratedFromRegionalPhrases from "../dict/STPhrases_GeneratedFromRegionalPhrases.js";
import dict_TWPhrases from "../dict/TWPhrases.js";
import dict_TWVariants from "../dict/TWVariants.js";
import dict_TWVariantsPhrases from "../dict/TWVariantsPhrases.js";

const fromDicts = {
    cn: from_cn
};

const toDicts = {
    hk: to_hk,
    hkp: to_hkp,
    tw: to_tw,
    twp: to_twp,
    jp: to_jp
};

const configs = {
    s2hk: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_HKVariantsPhrases, dict_HKVariants]] },
    s2hkp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_HKPhrases, dict_HKVariantsPhrases, dict_HKVariants]] },
    s2t: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters]] },
    s2tw: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_TWVariantsPhrases, dict_TWVariants]] },
    s2twp: { normalizationChain: [[dict_CJK_Compatibility_Ideographs]], segmentation: [dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases], conversionChain: [[dict_STPhrases, dict_STPhrases_GeneratedFromRegionalPhrases, dict_STCharacters], [dict_TWPhrases, dict_TWVariantsPhrases, dict_TWVariants]] }
};

export {fromDicts as from, toDicts as to, configs};