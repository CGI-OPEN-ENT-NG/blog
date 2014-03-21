package org.entcore.blog.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.*;
import static org.entcore.common.user.UserUtils.getUserInfos;

import fr.wseduc.mongodb.MongoDb;
import org.entcore.blog.security.BlogResourcesProvider;
import org.entcore.blog.services.BlogTimelineService;
import org.entcore.blog.services.PostService;
import org.entcore.blog.services.impl.DefaultBlogTimelineService;
import org.entcore.blog.services.impl.DefaultPostService;
import org.entcore.common.neo4j.Neo;
import fr.wseduc.webutils.*;
import org.entcore.common.user.UserUtils;
import org.entcore.common.user.UserInfos;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import java.util.Map;

public class PostController extends Controller {

	private final PostService post;
	private final BlogTimelineService timelineService;

	public PostController(Vertx vertx, Container container,
						  RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions,
						  MongoDb mongo) {
		super(vertx, container, rm, securedActions);
		this.post = new DefaultPostService(mongo);
		this.timelineService = new DefaultBlogTimelineService(vertx, eb, container, new Neo(eb, log), mongo);
	}

	// TODO improve fields matcher and validater
	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void create(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					request.expectMultiPart(true);
					request.endHandler(new VoidHandler() {
						@Override
						protected void handle() {
							post.create(blogId, Utils.jsonFromMultimap(request.formAttributes()), user,
									defaultResponseHandler(request));
						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void update(final HttpServerRequest request) {
		final String postId = request.params().get("postId");
		if (postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		request.expectMultiPart(true);
		request.endHandler(new VoidHandler() {
			@Override
			protected void handle() {
				post.update(postId, Utils.jsonFromMultimap(request.formAttributes()),
						defaultResponseHandler(request));
			}
		});
	}

	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void delete(final HttpServerRequest request) {
		final String postId = request.params().get("postId");
		if (postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		post.delete(postId, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					renderJson(request, event.right().getValue(), 204);
				} else {
					JsonObject error = new JsonObject()
							.putString("error", event.left().getValue());
					renderJson(request, error, 400);
				}
			}
		});
	}

	@SecuredAction(value = "blog.read", type = ActionType.RESOURCE)
	public void get(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String postId = request.params().get("postId");
		if (blogId == null || blogId.trim().isEmpty() ||
				postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		post.get(blogId, postId, BlogResourcesProvider.getStateType(request), defaultResponseHandler(request));
	}

	@SecuredAction(value = "blog.read", type = ActionType.RESOURCE)
	public void list(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					post.list(blogId, BlogResourcesProvider.getStateType(request),
							user, arrayResponseHandler(request));
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void submit(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String postId = request.params().get("postId");
		if (blogId == null || blogId.trim().isEmpty() ||
				postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					post.submit(blogId, postId, user, new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> event) {
							if (event.isRight()) {
								if ("PUBLISHED".equals(event.right().getValue().getString("state"))) {
									getUserInfos(eb, request, new Handler<UserInfos>() {
										@Override
										public void handle(UserInfos user) {
											timelineService.notifyPublishPost(request, blogId, postId, user,
													container.config().getString("host", "http://localhost:8018") +
															pathPrefix + "?blog=" + blogId);
										}
									});
								}
								renderJson(request, event.right().getValue());
							} else {
								JsonObject error = new JsonObject()
										.putString("error", event.left().getValue());
								renderJson(request, error, 400);
							}
						}
					});
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void publish(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String postId = request.params().get("postId");
		if (blogId == null || blogId.trim().isEmpty() ||
				postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		post.publish(blogId, postId, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					getUserInfos(eb, request, new Handler<UserInfos>() {
						@Override
						public void handle(UserInfos user) {
							timelineService.notifyPublishPost(request, blogId, postId, user,
									container.config().getString("host", "http://localhost:8018") +
									pathPrefix + "?blog=" + blogId);
						}
					});
					renderJson(request, event.right().getValue());
				} else {
					JsonObject error = new JsonObject()
							.putString("error", event.left().getValue());
					renderJson(request, error, 400);
				}
			}
		});
	}

	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void unpublish(final HttpServerRequest request) {
		final String postId = request.params().get("postId");
		if (postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		post.unpublish(postId, defaultResponseHandler(request));
	}

	@SecuredAction(value = "blog.comment", type = ActionType.RESOURCE)
	public void comment(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String postId = request.params().get("postId");
		if (blogId == null || blogId.trim().isEmpty() ||
				postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		request.expectMultiPart(true);
		request.endHandler(new VoidHandler() {
			@Override
			protected void handle() {
				UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							post.addComment(blogId, postId, request.formAttributes().get("comment"),
									user, defaultResponseHandler(request));
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@SecuredAction(value = "blog.comment", type = ActionType.RESOURCE)
	public void deleteComment(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String commentId = request.params().get("commentId");
		if (blogId == null || blogId.trim().isEmpty() ||
				commentId == null || commentId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		request.expectMultiPart(true);
		request.endHandler(new VoidHandler() {
			@Override
			protected void handle() {
				UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							post.deleteComment(blogId, commentId, user,
									defaultResponseHandler(request));
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@SecuredAction(value = "blog.read", type = ActionType.RESOURCE)
	public void comments(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String postId = request.params().get("postId");
		if (blogId == null || blogId.trim().isEmpty() ||
				postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		request.expectMultiPart(true);
		request.endHandler(new VoidHandler() {
			@Override
			protected void handle() {
				UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							post.listComment(blogId, postId, user,
									arrayResponseHandler(request));
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@SecuredAction(value = "blog.manager", type = ActionType.RESOURCE)
	public void publishComment(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String commentId = request.params().get("commentId");
		if (blogId == null || blogId.trim().isEmpty() ||
				commentId == null || commentId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		post.publishComment(blogId, commentId, defaultResponseHandler(request));
	}

}
