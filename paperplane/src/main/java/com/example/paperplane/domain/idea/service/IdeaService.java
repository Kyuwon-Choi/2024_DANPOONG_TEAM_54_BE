package com.example.paperplane.domain.idea.service;

import com.example.paperplane.domain.file.service.S3Service;
import com.example.paperplane.domain.idea.dto.IdeaCatalogResponse;
import com.example.paperplane.domain.idea.dto.IdeaDetailResponse;
import com.example.paperplane.domain.idea.dto.IdeaRequest;
import com.example.paperplane.domain.idea.entity.Category;
import com.example.paperplane.domain.idea.entity.Idea;
import com.example.paperplane.domain.idea.repository.IdeaRepository;
import com.example.paperplane.domain.purchase.repository.PurchaseRepository;
import com.example.paperplane.domain.user.entity.User;
import com.example.paperplane.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Service
@RequiredArgsConstructor
public class IdeaService {
    private final IdeaRepository ideaRepository;
    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final S3Service s3Service;

    public List<IdeaCatalogResponse> getIdeasByCategory(Category category) {
        return ideaRepository.findByCategory(category)
                .stream()
                .map(IdeaCatalogResponse::new)
                .collect(Collectors.toList());
    }

    public List<IdeaCatalogResponse> getAllIdeas() {
        return ideaRepository.findAll().stream()
                .map(IdeaCatalogResponse::new)
                .collect(Collectors.toList());
    }

    public List<IdeaCatalogResponse> getIdeasByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> ideaRepository.findByUser(user).stream()
                        .map(IdeaCatalogResponse::new)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new IllegalArgumentException("User not found: username = " + username));
    }

    public Idea createIdea(IdeaRequest request, Long userId, MultipartFile file, Category category) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Idea idea = new Idea(
                user,
                request.title(),
                category,
                request.description(),
                request.tags(),
                request.price()
        );

        if (file != null && !file.isEmpty()) {
            String fileUrl = s3Service.uploadFile(file);
            idea.setFileUrl(fileUrl);
        } else {
            idea.setFileUrl(null);
        }
        return ideaRepository.save(idea);
    }


    public IdeaDetailResponse getIdeaDetail(Long id, Long userId) {
        Idea idea = ideaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 아이디어를 찾을 수 없습니다: ID = " + id));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 유저를 찾을 수 없습니다: ID = " + userId));

        String status;

        if (idea.getUser().getUserId().equals(userId)) {
            status = "OWN"; // 본인 아이디어
        } else if (purchaseRepository.existsByUser_UserIdAndIdea_IdeaId(userId, id)) {
            status = "PURCHASED"; // 구매한 아이디어
        } else {
            status = "NOT_PURCHASED"; // 구매하지 않은 아이디어
        }

        return new IdeaDetailResponse(
                idea.getIdeaId(),
                idea.getTitle(),
                idea.getCategory(),
                idea.getDescription(),
                idea.getTags(),
                idea.getPrice(),
                idea.getUser().getUsername(),
                idea.getCreatedAt(),
                status
        );
    }

    public List<IdeaCatalogResponse> searchIdeas(String keyword) {
        return ideaRepository.searchByKeyword(keyword).stream()
                .map(IdeaCatalogResponse::new)
                .collect(Collectors.toList());
    }

    public String getIdeaFile(Long ideaId, Long userId) {
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Idea not found: ID = " + ideaId));

        if (!purchaseRepository.existsByUser_UserIdAndIdea_IdeaId(userId, ideaId) &&
                !idea.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not have access to this file");
        }

        return idea.getFileUrl();

    }

    public void deleteIdea(Long ideaId, Long userId) {
        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Idea not found: ID = " + ideaId));

        if (!idea.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not have access to this idea");
        }

        ideaRepository.delete(idea);
    }

    public Idea updateIdea(Long ideaId, Long userId, IdeaRequest request, MultipartFile file) {

        Idea idea = ideaRepository.findById(ideaId)
                .orElseThrow(() -> new IllegalArgumentException("Idea not found: ID = " + ideaId));


        if (!idea.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("User does not have access to this idea");
        }

        idea.update(request.title(),
                request.description(),
                request.tags(),
                request.price(),
                Category.fromDisplayName(request.categoryDisplayName()));

        if (file != null && !file.isEmpty()) {

            String fileUrl = s3Service.uploadFile(file);
            idea.setFileUrl(fileUrl);
        } else if (request.file() == null) {

            idea.setFileUrl(null);
        }


        ideaRepository.save(idea);
        return idea;
    }


}

