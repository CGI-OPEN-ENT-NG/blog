/*
 * Copyright © "Open Digital Education" (SAS “WebServices pour l’Education”), 2014
 *
 * This program is published by "Open Digital Education" (SAS “WebServices pour l’Education”).
 * You must indicate the name of the software and the company in any production /contribution
 * using the software and indicate on the home page of the software industry in question,
 * "powered by Open Digital Education" with a reference to the website: https: //opendigitaleducation.com/.
 *
 * This program is free software, licensed under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, version 3 of the License.
 *
 * You can redistribute this application and/or modify it since you respect the terms of the GNU Affero General Public License.
 * If you modify the source code and then use this modified source code in your creation, you must make available the source code of your modifications.
 *
 * You should have received a copy of the GNU Affero General Public License along with the software.
 * If not, please see : <http://www.gnu.org/licenses/>. Full compliance requires reading the terms of this license and following its directives.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.blog.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;
import static org.entcore.common.user.UserUtils.getUserInfos;

import java.util.Map;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.webutils.http.BaseController;
import fr.wseduc.webutils.request.RequestUtils;

import org.entcore.blog.security.BlogResourcesProvider;
import org.entcore.blog.services.BlogTimelineService;
import org.entcore.blog.services.PostService;
import org.entcore.blog.services.impl.DefaultBlogTimelineService;
import org.entcore.blog.services.impl.DefaultPostService;
import org.entcore.common.neo4j.Neo;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Controller;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Utils;

public class PostController extends BaseController {

	private PostService post;
	private BlogTimelineService timelineService;
	private int pagingSize;

	public void init(Vertx vertx, Container container, RouteMatcher rm,
					 Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, container, rm, securedActions);
		MongoDb mongo = MongoDb.getInstance();
		this.post = new DefaultPostService(mongo);
		this.timelineService = new DefaultBlogTimelineService(vertx, eb, container, new Neo(vertx, eb, log), mongo);
	}

	public PostController(final int pagingSize) {
		this.pagingSize = pagingSize;
	}

	// TODO improve fields matcher and validater
	@Post("/post/:blogId")
	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void create(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
			public void handle(final JsonObject data) {
				UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							post.create(blogId, data, user, defaultResponseHandler(request));
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@Put("/post/:blogId/:postId")
	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void update(final HttpServerRequest request) {
		final String postId = request.params().get("postId");
		if (postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
			public void handle(final JsonObject data) {
				post.update(postId, data, defaultResponseHandler(request));
			}
		});
	}

	@Delete("/post/:blogId/:postId")
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

	@Get("/post/:blogId/:postId")
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

	@Get("/post/list/all/:blogId")
	@SecuredAction(value = "blog.read", type = ActionType.RESOURCE)
	public void list(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		if (blogId == null || blogId.trim().isEmpty()) {
			badRequest(request);
			return;
		}

		final Integer page;

		try {
			page =  (request.params().get("page") != null) ? Integer.parseInt(request.params().get("page")) : null;
		}catch (NumberFormatException e) {
			badRequest(request, e.getMessage());
			return;
		}

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					if(request.params().get("state") == null){
						post.list(blogId, user, page, pagingSize, arrayResponseHandler(request));
					} else {
						post.list(blogId, BlogResourcesProvider.getStateType(request),
							user, page, pagingSize, arrayResponseHandler(request));
					}
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@Put("/post/submit/:blogId/:postId")
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
									timelineService.notifyPublishPost(request, blogId, postId, user,
											pathPrefix + "#/view/" + blogId);
								}
								else if ("SUBMITTED".equals(event.right().getValue().getString("state"))) {
									timelineService.notifySubmitPost(request, blogId, postId, user,
											pathPrefix + "#/view/" + blogId);
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

	@Put("/post/publish/:blogId/:postId")
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
									pathPrefix + "#/view/" + blogId);
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

	@Put("/post/unpublish/:blogId/:postId")
	@SecuredAction(value = "blog.contrib", type = ActionType.RESOURCE)
	public void unpublish(final HttpServerRequest request) {
		final String postId = request.params().get("postId");
		if (postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		post.unpublish(postId, defaultResponseHandler(request));
	}

	@Post("/comment/:blogId/:postId")
	@SecuredAction(value = "blog.comment", type = ActionType.RESOURCE)
	public void comment(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String postId = request.params().get("postId");
		if (blogId == null || blogId.trim().isEmpty() ||
				postId == null || postId.trim().isEmpty()) {
			badRequest(request);
			return;
		}
		RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
			public void handle(final JsonObject data) {
				UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
					@Override
					public void handle(final UserInfos user) {
						if (user != null) {
							Handler<Either<String, JsonObject>> notifyHandler = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										timelineService.notifyPublishComment(request, blogId, postId, user,
												pathPrefix + "#/view/" + blogId);
										renderJson(request, event.right().getValue());
									} else {
										JsonObject error = new JsonObject().putString("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							};
							post.addComment(blogId, postId, data.getString("comment"), user, notifyHandler);
						} else {
							unauthorized(request);
						}
					}
				});
			}
		});
	}

	@Delete("/comment/:blogId/:postId/:commentId")
	@SecuredAction(value = "blog.comment", type = ActionType.RESOURCE)
	public void deleteComment(final HttpServerRequest request) {
		final String blogId = request.params().get("blogId");
		final String commentId = request.params().get("commentId");
		if (blogId == null || blogId.trim().isEmpty() ||
				commentId == null || commentId.trim().isEmpty()) {
			badRequest(request);
			return;
		}

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

	@Get("/comments/:blogId/:postId")
	@SecuredAction(value = "blog.read", type = ActionType.RESOURCE)
	public void comments(final HttpServerRequest request) {
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
					post.listComment(blogId, postId, user,
							arrayResponseHandler(request));
				} else {
					unauthorized(request);
				}
			}
		});
	}

	@Put("/comment/:blogId/:postId/:commentId")
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
