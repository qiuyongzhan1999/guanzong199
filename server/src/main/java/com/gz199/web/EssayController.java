package com.gz199.web;

import com.gz199.ai.EssayGradeService;
import com.gz199.ai.EssayOcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/essay")
public class EssayController {
    private final EssayGradeService essayGrade;
    private final EssayOcrService essayOcr;

    public EssayController(EssayGradeService essayGrade, EssayOcrService essayOcr) {
        this.essayGrade = essayGrade;
        this.essayOcr = essayOcr;
    }

    /** AI 批改：粘贴正文 → DeepSeek 结构化阅卷反馈。 */
    @PostMapping("/grade")
    public Map<String, Object> grade(@RequestBody Map<String, Object> body) {
        return essayGrade.grade(body == null ? Map.of() : body);
    }

    /** 拍照识字：图片 base64 → DeepSeek 视觉模型 OCR。 */
    @PostMapping("/ocr")
    public Map<String, Object> ocr(@RequestBody Map<String, Object> body) {
        return essayOcr.recognize(body == null ? Map.of() : body);
    }
}
